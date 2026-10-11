package org.videolan.vlc.premiere;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Artwork from actual library items. All storage, metadata, decoding and HTTP work is off the UI. */
final class Artwork {
  private static final Handler main=new Handler(Looper.getMainLooper());
  private static ThreadPoolExecutor executor(int threads) {
    ThreadPoolExecutor pool=new ThreadPoolExecutor(threads,threads,30,TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>(128));pool.prestartAllCoreThreads();return pool;
  }
  private static final ThreadPoolExecutor io=executor(2), network=executor(1), movieNetwork=executor(1);
  private static final ExecutorService sweeper=Executors.newSingleThreadExecutor();
  private static final Object sweepLock=new Object();
  private static Object[] sweepVideos=new Object[0],sweepAudios=new Object[0];
  private static Context sweepContext;
  private static boolean sweepRunning;
  private static long sweepRevision,processedRevision,completedCount,sweepTotal;
  private static boolean retryScheduled;
  private static final Set<String> completedSweep=new HashSet<>();
  private static final Map<String,Job> jobs=new HashMap<>();
  private static final Map<String,List<WeakReference<ImageView>>> subscribers=new HashMap<>();
  private static final android.util.LruCache<String,Bitmap> memory=new android.util.LruCache<String,Bitmap>(16*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
  private static final long TTL=90L*86400000L, MISS_TTL=86400000L;
  private static final int MISS_SCHEMA=6;
  private static long lastMusicRequest,lastDeezerRequest,lastYoutubeRequest; private static volatile long retryMovieAfter,retryMusicAfter;
  private static volatile String lastNetworkStatus="No lookup errors reported";
  private static volatile String imageBase, posterSize="w342";
  private static final String USER_AGENT="Premiere-local-library/1.0 (personal VLC redesign; https://www.videolan.org/)";
  private static final class Binding { final String identity,key; final Object media; int priority; Binding(String s,String k,Object m){identity=s;key=k;media=m;} }
  private static final class Job {
    final String key;
    final List<CountDownLatch> completions=new ArrayList<>();
    Bitmap preview;
    int priority;
    boolean foreground;
    Runnable ioTask,networkTask;
    ThreadPoolExecutor networkPool;
    Job(String k){key=k;}
  }
  private static final class Data {
    String uri="",filename="",title="",artist="",album="",date="",fingerprint="";
    boolean video; Bitmap local; int localPriority;
  }
  private static final class Match {
    String provider,id,title,year,url,query; double score;
    final List<Match> alternatives=new ArrayList<>();
    Match(String p,String i,String t,String y,String u,String q,double s){provider=p;id=i;title=t;year=y;url=u;query=q;score=s;}
  }
  private static final class HttpError extends IOException {final int status;HttpError(int s){super("HTTP "+s);status=s;}}
  static void invalidateMemory(){ synchronized(jobs){memory.evictAll();} }
  static String status(Context context){
    boolean token=new File(context.getNoBackupFilesDir(),"premiere_tmdb_token").isFile();
    long done,total; synchronized(sweepLock){done=completedCount;total=sweepTotal;}
    String state;
    if(!token)state="Movie posters need a TMDB read token.";
    else if(System.currentTimeMillis()<retryMovieAfter||System.currentTimeMillis()<retryMusicAfter)state="A provider is temporarily unavailable; artwork will retry automatically.";
    else state=lastNetworkStatus;
    return "Library scan: "+done+" of "+total+" items checked. "+state;
  }
  /** Walk the latest complete library snapshot serially, without retaining views or activities. */
  static void sweep(Context context,Object[] videos,Object[] audios) {
    synchronized(sweepLock){sweepContext=context.getApplicationContext();sweepVideos=videos==null?new Object[0]:videos.clone();sweepAudios=audios==null?new Object[0]:audios.clone();sweepRevision++;if(sweepRunning)return;sweepRunning=true;}
    try { sweeper.execute(Artwork::sweepLoop); }
    catch(RejectedExecutionException stopped){synchronized(sweepLock){sweepRunning=false;}}
  }
  private static void sweepLoop(){
    try { while(true){Context app;Object[] all;long revision;
      synchronized(sweepLock){app=sweepContext;revision=sweepRevision;if(processedRevision==revision)return;processedRevision=revision;all=Arrays.copyOf(sweepVideos,sweepVideos.length+sweepAudios.length);System.arraycopy(sweepAudios,0,all,sweepVideos.length,sweepAudios.length);sweepTotal=all.length;completedCount=0;}
      for(Object media:prioritize(app,all)){
        if(media==null){synchronized(sweepLock){completedCount++;}continue;}
        String key=identity(media);
        synchronized(sweepLock){if(revision!=sweepRevision)break;if(completedSweep.contains(key)){completedCount++;continue;}}
        try { scanOne(app,media); }
        catch(RuntimeException failure){android.util.Log.w("PremiereArtwork","Artwork item scan failed",failure);}
        synchronized(sweepLock){if(revision==sweepRevision)completedSweep.add(key);completedCount++;}
      }
    }} finally {
      boolean again;
      synchronized(sweepLock){sweepRunning=false;again=processedRevision!=sweepRevision;if(again)sweepRunning=true;}
      if(again)try{sweeper.execute(Artwork::sweepLoop);}catch(RejectedExecutionException stopped){synchronized(sweepLock){sweepRunning=false;}}
    }
  }
  /** Revisit cached fallbacks after metadata changes, settings, or a manual retry. */
  static void revisit(){synchronized(sweepLock){completedSweep.clear();sweepRevision++;}}
  private static Object[] prioritize(Context app,Object[] items){Object[] ordered=items.clone();Arrays.sort(ordered,(left,right)->{long a=priority(app,left),b=priority(app,right);int cached=Long.compare(b,a);if(cached!=0)return cached;return Long.compare(newest(right),newest(left));});return ordered;}
  private static long newest(Object media){return Math.round(Premiere.number(Premiere.invoke(media,"getId")));}
  private static long priority(Context app,Object media){String key=sha(identity(media));File dir=cache(app);if(!new File(dir,"media-"+key+".json").exists()&&!new File(dir,"media-"+key+".miss").exists())return Long.MAX_VALUE;try{Uri uri=Uri.parse(Premiere.str(Premiere.invoke(media,"getUri")));if("file".equals(uri.getScheme()))return new File(uri.getPath()).lastModified();}catch(Exception ignored){}return 0;}
  private static long retryDelay(){long now=System.currentTimeMillis(),movie=retryMovieAfter-now,music=retryMusicAfter-now;long next=movie>0&&music>0?Math.min(movie,music):Math.max(movie,music);return Math.max(15000,next);}
  private static void retrySweepLater(Context app){synchronized(sweepLock){if(retryScheduled||sweepVideos==null||sweepAudios==null)return;retryScheduled=true;}main.postDelayed(()->{Object[] vs,as;synchronized(sweepLock){vs=sweepVideos;as=sweepAudios;retryScheduled=false;completedSweep.clear();}retryVisible(app);if(vs!=null&&as!=null)sweep(app,vs,as);},retryDelay());}
  private static void retryVisible(Context app){List<WeakReference<ImageView>> views=new ArrayList<>();synchronized(jobs){for(List<WeakReference<ImageView>> list:subscribers.values())views.addAll(list);}for(WeakReference<ImageView> ref:views){ImageView view=ref.get();if(view!=null&&view.isAttachedToWindow()&&view.getTag() instanceof Binding){Binding binding=(Binding)view.getTag();resolve(app,binding.media,binding.identity,view);}}}
  private static void scanOne(Context app,Object media){if(media==null)return;CountDownLatch done=new CountDownLatch(1);resolve(app,media,identity(media),null,done);try{done.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  private static String identity(Object media){return ArtworkMatch.itemIdentity(Premiere.str(Premiere.invoke(media,"getUri")),Premiere.str(Premiere.invoke(media,"getId")),Premiere.str(Premiere.invoke(media,"getTitle")),Premiere.str(Premiere.invoke(media,"getDate")),Premiere.number(Premiere.invoke(media,"getLength")));}
  static void clearMisses(Context c,Runnable done){submit(io,()->{File[] fs=cache(c).listFiles();if(fs!=null)for(File f:fs)if(f.getName().endsWith(".miss"))f.delete();revisit();if(done!=null)main.post(done);});}
  static void clearCache(Context c,Runnable done){invalidateMemory();submit(network,()->{File[] fs=cache(c).listFiles();if(fs!=null)for(File f:fs)f.delete();revisit();if(done!=null)main.post(done);});}
  static void saveToken(Context app,String token)throws Exception {write(new File(app.getNoBackupFilesDir(),"premiere_tmdb_token"),token);}
  static void validate(String token,java.util.function.Consumer<Boolean> done){
    if(!submit(movieNetwork,()->{boolean ok=false;try{json("https://api.themoviedb.org/3/configuration",token);ok=true;}catch(Exception ignored){}done.accept(ok);}))done.accept(false);
  }
  private static boolean submit(Executor executor,Runnable task){synchronized(jobs){try{executor.execute(task);return true;}catch(RejectedExecutionException full){return false;}}}
  private static boolean submitJob(ThreadPoolExecutor pool,Job job,Runnable task){synchronized(jobs){if(pool==io)job.ioTask=task;else{job.networkTask=task;job.networkPool=pool;}if(job.foreground)return ((LinkedBlockingDeque<Runnable>)pool.getQueue()).offerFirst(task);return submit(pool,task);}}
  private static void promote(ThreadPoolExecutor pool,Runnable task){if(task!=null&&pool.getQueue().remove(task)){if(!((LinkedBlockingDeque<Runnable>)pool.getQueue()).offerFirst(task))submit(pool,task);}}
  static void resolve(Context context,Object media,String identity,ImageView view) {
    resolve(context,media,identity,view,null);
  }
  private static void resolve(Context context,Object media,String identity,ImageView view,CountDownLatch done) {
    Context app=context.getApplicationContext(); String key=sha(identity); Binding binding=view==null?null:new Binding(identity,key,media); if(view!=null)view.setTag(binding);
    if(view!=null)main.postDelayed(()->{if(view.getTag()!=binding||!view.getGlobalVisibleRect(new android.graphics.Rect()))return;synchronized(jobs){Job pending=jobs.get(key);if(pending!=null){pending.foreground=true;promote(io,pending.ioTask);promote(pending.networkPool==null?network:pending.networkPool,pending.networkTask);}}},100);
    Job job;
    synchronized(jobs){if(view!=null){if(subscribers.size()>128)pruneSubscribers();List<WeakReference<ImageView>> views=subscribers.get(key);if(views==null){views=new ArrayList<>();subscribers.put(key,views);}for(Iterator<WeakReference<ImageView>> it=views.iterator();it.hasNext();){ImageView old=it.next().get();if(old==null||old==view)it.remove();}views.add(new WeakReference<>(view));}
      Bitmap hit=memory.get(key);if(hit!=null){if(view!=null){view.setImageBitmap(hit);binding.priority=2;}if(done!=null)done.countDown();return;}
      job=jobs.get(key);if(job!=null){if(view!=null&&job.preview!=null)deliver(view,binding,job.preview,job.priority);if(done!=null)job.completions.add(done);return;}
      job=new Job(key);if(done!=null)job.completions.add(done);jobs.put(key,job);}
    final Job current=job;
    if(!submitJob(io,current,()->{
      try {
        Data d=metadata(app,media,identity);
        if(d.local!=null)publish(current,d.local,d.localPriority);
        if(d.localPriority>=3){finish(key,current,d.local);return;}
        File index=new File(cache(app),"media-"+key+".json");
        Bitmap cached=cached(app,index,d.fingerprint);
        if(cached!=null){publish(current,cached,2);if(!needsYoutubeUpgrade(app,d,index)){finish(key,current,cached);return;}}
        final Bitmap prior=cached;
        if(!enabled(app,d.video)||d.video&&ArtworkMatch.camera(d.filename)){finish(key,current,d.local);return;}
        File miss=new File(cache(app),"media-"+key+".miss");
        if(miss.isFile())try{JSONObject m=new JSONObject(read(miss));if(m.optInt("schema")==MISS_SCHEMA&&d.fingerprint.equals(m.optString("fingerprint"))&&System.currentTimeMillis()<m.optLong("retry")){finish(key,current,d.local);return;}}catch(Exception ignored){}
        ThreadPoolExecutor providerQueue=d.video?movieNetwork:network;
        if(!submitJob(providerQueue,current,()->{
          Bitmap found=null;
          try {
            if(d.video&&System.currentTimeMillis()<retryMovieAfter)return;
            found=cached(app,index,d.fingerprint);
            boolean upgrade=needsYoutubeUpgrade(app,d,index);
            if(found==null||upgrade){
              found=d.video?cacheMatch(app,d,key,movie(app,d)):musicArtwork(app,d,key);
              if(upgrade&&found!=null)markYoutubeChecked(index);
              if(found==null&&prior!=null){found=prior;markYoutubeChecked(index);}
              if(found==null){JSONObject m=new JSONObject();m.put("schema",MISS_SCHEMA).put("fingerprint",d.fingerprint).put("retry",System.currentTimeMillis()+MISS_TTL);write(miss,m.toString());trim(cache(app));}
            }
            if(found!=null){lastNetworkStatus="Artwork is loading automatically.";android.util.Log.i("PremiereArtwork",d.video?"Movie poster cached":"Music cover cached");publish(current,found,2);}
          }catch(Exception failure){ // Offline/transient failures are never catalogue misses.
            int code=failure instanceof HttpError?((HttpError)failure).status:0;
            android.util.Log.w("PremiereArtwork",(d.video?"Movie":"Music")+" lookup: "+(code==0?failure.getClass().getSimpleName():"HTTP "+code));
            long retry=System.currentTimeMillis()+(code==429||code==503||code==500?60000:15000);
            if(d.video)retryMovieAfter=retry;else retryMusicAfter=retry;
            if(found==null)found=prior;
            if(found!=null)publish(current,found,2);
            lastNetworkStatus=found==null?"A provider is temporarily unavailable; artwork will retry automatically.":"Artwork is loading automatically using a fallback source.";
            retrySweepLater(app);
          }finally{finish(key,current,found==null?d.local:found);}
        })) {retrySweepLater(app);finish(key,current,d.local);}
      }catch(Exception failure){lastNetworkStatus="Artwork metadata could not be read for some items.";finish(key,current,null);}
    })) {retrySweepLater(app);finish(key,current,null);}
  }
  private static Bitmap cacheMatch(Context app,Data d,String key,Match match)throws Exception {
    if(match==null)return null;List<Match> choices=new ArrayList<>();choices.add(match);choices.addAll(match.alternatives);
    for(Match choice:choices){File image=new File(cache(app),"entity-"+sha(choice.provider+"|"+choice.id)+".jpg"),age=new File(image.getPath()+".age");Bitmap found=null;
      if(image.isFile()&&age.isFile())try{if(System.currentTimeMillis()-Long.parseLong(read(age))<TTL)found=decodeFile(image);}catch(Exception ignored){}
      if(found==null){found=bitmap(choice.url);if(found!=null){saveBitmap(image,found);write(age,""+System.currentTimeMillis());}}
      if(found!=null){JSONObject m=new JSONObject();m.put("fingerprint",d.fingerprint).put("provider",choice.provider).put("id",choice.id).put("query",choice.query).put("matched_title",choice.title).put("year",choice.year).put("confidence",choice.score).put("image",image.getName()).put("saved",System.currentTimeMillis());write(new File(cache(app),"media-"+key+".json"),m.toString());new File(cache(app),"media-"+key+".miss").delete();trim(cache(app));android.util.Log.i("PremiereArtwork","Artwork cached from "+choice.provider);return found;}
    }return null;
  }
  private static boolean enabled(Context c,boolean video){SharedPreferences p=c.getSharedPreferences(c.getPackageName()+"_preferences",Context.MODE_PRIVATE);return video?p.getBoolean("premiere_video_artwork",true)&&new File(c.getNoBackupFilesDir(),"premiere_tmdb_token").isFile():p.getBoolean("premiere_music_artwork",true);}
  private static void deliver(ImageView view,Binding binding,Bitmap image,int priority){main.post(()->{if(view.getTag()==binding&&priority>=binding.priority){binding.priority=priority;view.setImageBitmap(image);}});}
  private static void pruneSubscribers(){for(Iterator<Map.Entry<String,List<WeakReference<ImageView>>>> entries=subscribers.entrySet().iterator();entries.hasNext();){Map.Entry<String,List<WeakReference<ImageView>>> entry=entries.next();for(Iterator<WeakReference<ImageView>> it=entry.getValue().iterator();it.hasNext();){ImageView v=it.next().get();if(v==null||!(v.getTag() instanceof Binding)||!entry.getKey().equals(((Binding)v.getTag()).key))it.remove();}if(entry.getValue().isEmpty())entries.remove();}}
  private static void publish(Job job,Bitmap image,int priority){synchronized(jobs){if(priority>=job.priority){job.preview=image;job.priority=priority;}List<WeakReference<ImageView>> views=subscribers.get(job.key);if(views==null)return;for(Iterator<WeakReference<ImageView>> it=views.iterator();it.hasNext();){ImageView v=it.next().get();if(v==null||!(v.getTag() instanceof Binding)||!job.key.equals(((Binding)v.getTag()).key)){it.remove();continue;}deliver(v,(Binding)v.getTag(),image,priority);}if(views.isEmpty())subscribers.remove(job.key);}}
  private static void finish(String key,Job job,Bitmap image){synchronized(jobs){if(image!=null&&job.priority>=2)memory.put(key,image);if(jobs.get(key)==job)jobs.remove(key);for(CountDownLatch latch:job.completions)latch.countDown();job.completions.clear();}}
  private static String text(Object media,String method){return ArtworkMatch.known(Premiere.str(Premiere.invoke(media,method)));}
  private static Data metadata(Context c,Object media,String identity) throws Exception {
    Data d=new Data();Object source=media;d.uri=Premiere.str(Premiere.invoke(media,"getUri"));
    if(d.uri.isEmpty()){Object tracks=Premiere.invoke(media,"getTracks");if(tracks instanceof Object[]&&((Object[])tracks).length>0){source=((Object[])tracks)[0];d.uri=Premiere.str(Premiere.invoke(source,"getUri"));}}
    d.video=!d.uri.isEmpty()&&Premiere.number(Premiere.invoke(source,"getType"))==0;
    d.filename=text(source,"getFileName");Uri uri=Uri.parse(d.uri);if(d.filename.isEmpty())d.filename=uri.getLastPathSegment()==null?"":uri.getLastPathSegment();
    d.title=text(source,"getTitle");d.artist=text(source,"getAlbumArtistName");if(d.artist.isEmpty())d.artist=text(source,"getArtistName");d.album=text(source,"getAlbumName");d.date=text(source,"getDate");
    long size=0,modified=0;
    if("file".equals(uri.getScheme())||uri.getScheme()==null){File file=new File(uri.getPath()==null?"":uri.getPath());size=file.length();modified=file.lastModified();}
    else if("content".equals(uri.getScheme()))try(Cursor cursor=c.getContentResolver().query(uri,new String[]{"_display_name","_size","date_modified"},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){int n=cursor.getColumnIndex("_display_name"),s=cursor.getColumnIndex("_size"),m=cursor.getColumnIndex("date_modified");if(n>=0)d.filename=cursor.getString(n);if(s>=0)size=cursor.getLong(s);if(m>=0)modified=cursor.getLong(m);}}catch(Exception ignored){}
    String local=text(source,"getArtworkMrl");
    if(!local.isEmpty()&&!local.startsWith("http"))try{Uri art=Uri.parse(local);try(InputStream in=art.getScheme()==null?new FileInputStream(local):c.getContentResolver().openInputStream(art)){if(in!=null)d.local=decode(bytes(in,8*1024*1024));}if(d.local!=null)d.localPriority=d.video?1:3;}catch(Exception ignored){}
    if(!d.uri.isEmpty()&&Arrays.asList("file","content",null).contains(uri.getScheme())){
      MediaMetadataRetriever mm=new MediaMetadataRetriever();try{mm.setDataSource(c,uri);
        String title=ArtworkMatch.known(mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));if(!title.isEmpty())d.title=title;
        String artist=ArtworkMatch.known(mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));if(artist.isEmpty())artist=ArtworkMatch.known(mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));if(!artist.isEmpty())d.artist=artist;
        String album=ArtworkMatch.known(mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));if(!album.isEmpty())d.album=album;
        String date=ArtworkMatch.known(mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));if(!date.isEmpty())d.date=date;
        if(d.local==null){byte[] cover=mm.getEmbeddedPicture();if(cover!=null&&cover.length<8*1024*1024){d.local=decode(cover);d.localPriority=3;}else if(d.video){d.local=mm.getScaledFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,640,360);d.localPriority=1;}}
      }catch(Exception ignored){}finally{mm.release();}
    }
    // Entity cards can supply useful album identity even when their first track is untagged.
    if(media!=source){if(d.album.isEmpty())d.album=text(media,"getTitle");if(d.artist.isEmpty())d.artist=text(media,"getAlbumArtistName");}
    if(d.video){
      SharedPreferences preferences=c.getSharedPreferences(c.getPackageName()+"_preferences",Context.MODE_PRIVATE);
      String manual=preferences.getString("premiere_artwork_override_"+d.uri,"").trim();
      if(!manual.isEmpty())d.title=manual;
    }
    d.fingerprint=sha(identity+"|"+size+"|"+modified+"|"+d.title+"|"+d.artist+"|"+d.album+"|"+d.date+"|"+d.filename);
    return d;
  }
  private static boolean needsYoutubeUpgrade(Context app,Data d,File index){
    if(d.video||!enabled(app,false))return false;
    try{JSONObject m=new JSONObject(read(index));boolean approximate=ArtworkMatch.liveSet(d.filename+" "+d.title)||m.optString("query").contains("representative release");return approximate&&!"youtube".equals(m.optString("provider"))&&System.currentTimeMillis()-m.optLong("youtube_checked")>MISS_TTL;}catch(Exception ignored){return false;}
  }
  private static void markYoutubeChecked(File index){try{JSONObject m=new JSONObject(read(index));m.put("youtube_checked",System.currentTimeMillis());write(index,m.toString());}catch(Exception ignored){}}
  /** A failed provider must not prevent the other independent artwork sources. */
  private static Bitmap musicArtwork(Context app,Data d,String key)throws Exception {
    ArtworkMatch.Music clues=ArtworkMatch.music(d.filename,d.title,d.artist,d.album);
    Match direct=youtubeDirect(clues,d);Bitmap image=cacheMatch(app,d,key,direct);if(image!=null)return image;
    boolean live=ArtworkMatch.liveSet(d.filename+" "+d.title),youtubeTried=false;Exception failure=null;
    if(live){youtubeTried=true;try{image=cacheMatch(app,d,key,youtube(app,d));if(image!=null)return image;}catch(Exception unavailable){failure=unavailable;}}
    Match catalog=null;
    if(System.currentTimeMillis()>=retryMusicAfter)try{catalog=music(app,d);}catch(Exception unavailable){failure=unavailable;retryMusicAfter=System.currentTimeMillis()+60000;android.util.Log.w("PremiereArtwork","Music catalogue temporarily unavailable; trying alternate sources");}
    if(catalog==null)try{catalog=deezer(app,d);}catch(Exception unavailable){failure=unavailable;}
    boolean representative=catalog!=null&&catalog.query.contains("representative release");
    if(!youtubeTried&&(catalog==null||representative)){try{image=cacheMatch(app,d,key,youtube(app,d));if(image!=null)return image;}catch(Exception unavailable){failure=unavailable;}}
    image=cacheMatch(app,d,key,catalog);if(image!=null)return image;
    // A missing CAA image can still have an album image in the alternate catalogue.
    if(catalog!=null&&"musicbrainz".equals(catalog.provider))try{image=cacheMatch(app,d,key,deezer(app,d));if(image!=null)return image;}catch(Exception unavailable){failure=unavailable;}
    if(failure!=null)throw failure;return null;
  }
  private static Bitmap cached(Context c,File index,String fingerprint){try{if(!index.isFile())return null;JSONObject m=new JSONObject(read(index));if(!fingerprint.equals(m.optString("fingerprint"))||System.currentTimeMillis()-m.optLong("saved")>=TTL)return null;String name=m.optString("image");if(!name.matches("entity-[a-f0-9]{64}\\.jpg"))return null;File image=new File(cache(c),name);Bitmap b=decodeFile(image);if(b!=null){index.setLastModified(System.currentTimeMillis());image.setLastModified(System.currentTimeMillis());}return b;}catch(Exception ignored){return null;}}
  private static Match movie(Context c,Data d)throws Exception {
    String filename=ArtworkMatch.videoTitle(d.filename),tag=ArtworkMatch.videoTitle(d.title),year=ArtworkMatch.year(d.filename);
    SharedPreferences preferences=c.getSharedPreferences(c.getPackageName()+"_preferences",Context.MODE_PRIVATE);
    String manual=preferences.getString("premiere_artwork_override_"+d.uri,"").trim();
    if(!manual.isEmpty()){
      filename=ArtworkMatch.videoTitle(manual);
      tag=filename;
      year=ArtworkMatch.year(manual);
    }
    if(manual.isEmpty()&&year.isEmpty()&&d.date.matches("(?:19|20)\\d{2}.*"))year=d.date.substring(0,4);
    if(filename.isEmpty())filename=tag; if(tag.isEmpty())tag=filename;
    if(filename.isEmpty()||ArtworkMatch.score(filename,tag)<.65)return null;
    String token=read(new File(c.getNoBackupFilesDir(),"premiere_tmdb_token")).trim();if(token.isEmpty())return null;
    configuration(token);boolean tv=ArtworkMatch.episode(d.filename)||ArtworkMatch.episode(d.title);
    String queryKey="video|"+tv+"|"+ArtworkMatch.normalize(filename)+"|"+ArtworkMatch.normalize(tag)+"|"+year;
    Match previous=readMatch(c,queryKey);if(previous!=null)return previous;
    Map<String,Match> candidates=new HashMap<>();
    LinkedHashSet<String> queries=new LinkedHashSet<>();queries.add(filename);queries.add(tag);
    for(String query:queries){JSONArray rows=json("https://api.themoviedb.org/3/search/"+(tv?"tv":"movie")+"?query="+enc(query)+(!tv&&!year.isEmpty()?"&year="+enc(year):""),token).optJSONArray("results");if(rows==null)continue;
      for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String title=row.optString(tv?"name":"title"),original=row.optString(tv?"original_name":"original_title"),date=row.optString(tv?"first_air_date":"release_date"),cy=date.length()>=4?date.substring(0,4):"",path=row.optString("poster_path");if(path.isEmpty()||path.equals("null"))continue;
        if(!tv&&!year.isEmpty()&&!cy.isEmpty()&&!year.equals(cy))continue;
        double score=Math.max(Math.max(ArtworkMatch.score(filename,title),ArtworkMatch.score(tag,title)),Math.max(ArtworkMatch.score(filename,original),ArtworkMatch.score(tag,original)));
        if(tv&&!year.isEmpty()&&year.equals(cy))score+=.03;
        String id=(tv?"tv/":"movie/")+row.optString("id");Match match=new Match("tmdb",id,title,cy,imageBase+posterSize+path,query,score);put(candidates,match);
      }
    }
    Match selected=best(candidates,.88,.08);if(selected!=null)writeMatch(c,queryKey,selected);return selected;
  }
  private static void configuration(String token)throws Exception {
    if(imageBase!=null)return;JSONObject config=json("https://api.themoviedb.org/3/configuration",token);String base=config.getJSONObject("images").getString("secure_base_url");if(!base.startsWith("https://image.tmdb.org/"))throw new IOException("Unexpected image host");JSONArray sizes=config.getJSONObject("images").getJSONArray("poster_sizes"); if(!sizes.toString().contains("w342"))posterSize=sizes.getString(Math.min(3,sizes.length()-1));imageBase=base;
  }
  private static void put(Map<String,Match> rows,Match m){Match old=rows.get(m.id);if(old==null||m.score>old.score)rows.put(m.id,m);}
  private static Match best(Map<String,Match> rows,double minimum,double gap){Match top=null;double second=0;for(Match m:rows.values())if(top==null||m.score>top.score){if(top!=null)second=top.score;top=m;}else second=Math.max(second,m.score);return top!=null&&top.score>=minimum&&top.score-second>=gap?top:null;}
  private static String artist(JSONObject row){StringBuilder b=new StringBuilder();JSONArray credits=row.optJSONArray("artist-credit");if(credits!=null)for(int i=0;i<credits.length();i++){JSONObject credit=credits.optJSONObject(i);if(credit==null)continue;JSONObject ar=credit.optJSONObject("artist");b.append(credit.optString("name",ar==null?"":ar.optString("name"))).append(credit.optString("joinphrase"));}return b.toString();}
  private static Match youtubeDirect(ArtworkMatch.Music clues,Data d){String id=ArtworkMatch.youtubeId(d.filename);if(id.isEmpty())id=ArtworkMatch.youtubeId(d.title);if(id.isEmpty())return null;return new Match("youtube",id,clues.title,"","https://i.ytimg.com/vi/"+id+"/hqdefault.jpg","embedded video id",1);}
  private static Match youtube(Context c,Data d)throws Exception {
    ArtworkMatch.Music clues=ArtworkMatch.music(d.filename,d.title,d.artist,d.album);
    if(clues.title.isEmpty())return null;
    String queryKey="youtube2|"+clues.identity()+"|"+ArtworkMatch.normalize(d.filename)+"|"+ArtworkMatch.normalize(d.title);
    Match old=readMatch(c,queryKey);if(old!=null)return old;
    LinkedHashSet<String> queries=new LinkedHashSet<>();queries.add((clues.artist+" "+clues.title).trim());
    String qualifiers=ArtworkMatch.musicTitle(ArtworkMatch.unnumber(d.filename));
    if(ArtworkMatch.liveSet(qualifiers))queries.add(qualifiers);
    Map<String,Match> candidates=new HashMap<>();
    for(String query:queries){long wait=1100-(System.currentTimeMillis()-lastYoutubeRequest);if(wait>0)Thread.sleep(wait);lastYoutubeRequest=System.currentTimeMillis();
      byte[] data=download("https://www.youtube.com/results?search_query="+enc(query),null,4*1024*1024);
      String html=new String(data,"UTF-8"),marker="ytInitialData";int at=html.indexOf(marker);if(at<0)throw new IOException("Public video search unavailable");at=html.indexOf('{',at+marker.length());String object=ArtworkMatch.balancedObject(html,at);if(object.isEmpty())throw new IOException("Public video search unavailable");
      JSONObject root=new JSONObject(object);collectVideos(root,candidates,query,clues,0,new int[]{0});
      Match selected=youtubeBest(candidates);if(selected!=null){writeMatch(c,queryKey,selected);return selected;}
    }return null;
  }
  private static Match youtubeBest(Map<String,Match> candidates){List<Match> ranked=new ArrayList<>(candidates.values());ranked.sort((a,b)->{int score=Double.compare(b.score,a.score);return score==0?a.id.compareTo(b.id):score;});if(ranked.isEmpty()||ranked.get(0).score<.74)return null;Match selected=ranked.get(0);for(int i=1;i<Math.min(3,ranked.size());i++)if(ranked.get(i).score>=.74)selected.alternatives.add(ranked.get(i));return selected;}
  private static void collectVideos(Object node,Map<String,Match> out,String query,ArtworkMatch.Music clues,int depth,int[] visited){
    if(node==null||depth>40||visited[0]++>12000)return;
    if(node instanceof JSONObject){JSONObject o=(JSONObject)node;JSONObject video=o.optJSONObject("videoRenderer");if(video!=null){String id=video.optString("videoId"),title=rendererText(video.opt("title"));if(id.matches("[A-Za-z0-9_-]{11}")&&!title.isEmpty()){
        double score=ArtworkMatch.youtubeScore(clues.artist,clues.title,title);
        // A title must identify the track/set and include a credible artist match.
        if(score>=0){if(ArtworkMatch.liveSet(clues.title)&&ArtworkMatch.liveSet(title))score+=.04;
          JSONArray thumbs=video.optJSONObject("thumbnail") == null?null:video.optJSONObject("thumbnail").optJSONArray("thumbnails");List<String> urls=new ArrayList<>();if(thumbs!=null)for(int i=thumbs.length()-1;i>=0;i--){String u=thumbs.optJSONObject(i)==null?"":thumbs.optJSONObject(i).optString("url");if(safeYoutubeImage(u)&&!urls.contains(u))urls.add(u);}
          if(urls.isEmpty())urls.add("https://i.ytimg.com/vi/"+id+"/hqdefault.jpg");Match m=new Match("youtube",id,title,"",urls.get(0),query,score);for(int i=1;i<urls.size();i++)m.alternatives.add(new Match("youtube",id+"_"+i,title,"",urls.get(i),query,score));put(out,m);
        }}}
      JSONArray names=o.names();if(names!=null)for(int i=0;i<names.length();i++)collectVideos(o.opt(names.optString(i)),out,query,clues,depth+1,visited);
    }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectVideos(a.opt(i),out,query,clues,depth+1,visited);}
  }
  private static String rendererText(Object node){if(node instanceof JSONObject){JSONObject o=(JSONObject)node;String s=o.optString("simpleText");if(!s.isEmpty())return s;JSONArray runs=o.optJSONArray("runs");if(runs!=null){StringBuilder b=new StringBuilder();for(int i=0;i<runs.length();i++)b.append(runs.optJSONObject(i)==null?"":runs.optJSONObject(i).optString("text"));return b.toString();}}return "";}
  private static boolean safeYoutubeImage(String url){try{URL u=new URL(url);return "https".equals(u.getProtocol())&&"i.ytimg.com".equals(u.getHost())&&u.getPath().matches("/vi/[A-Za-z0-9_-]{11}/[^/]+\\.(jpg|webp)");}catch(Exception ignored){return false;}}
  private static Match music(Context c,Data d)throws Exception {
    ArtworkMatch.Music clues=ArtworkMatch.music(d.filename,d.title,d.artist,d.album);
    if(clues.title.isEmpty()&&clues.album.isEmpty())return null;
    boolean titleOnly=clues.artist.isEmpty();
    boolean album=!clues.album.isEmpty();String entity=album?"release-group":"recording",term=album?clues.album:clues.title;
    String queryKey="music4|"+album+"|"+clues.identity();Match previous=readMatch(c,queryKey);if(previous!=null&&new File(cache(c),"entity-"+sha(previous.provider+"|"+previous.id)+".jpg").isFile())return previous;
    Map<String,Match> candidates=new HashMap<>();
    for(int attempt=0;attempt<2;attempt++){
      String query=album?"releasegroup":"recording";
      String search=query+":"+(attempt==0?quote(term):fuzzy(term))+(titleOnly?"":" AND artist:"+(attempt==0?quote(clues.artist):fuzzy(clues.artist)));
      JSONArray rows=musicJson("https://musicbrainz.org/ws/2/"+entity+"/?query="+enc(search)+"&fmt=json&limit=25").optJSONArray(album?"release-groups":"recordings");if(rows==null)continue;
      for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);double titleScore=ArtworkMatch.score(term,row.optString("title")),artistScore=titleOnly?titleScore:ArtworkMatch.score(clues.artist,artist(row));if(titleScore<.75||artistScore<.75)continue;double score=.72*titleScore+.28*artistScore+.03*row.optDouble("score",0)/100;
        if(album){String id=row.optString("id");put(candidates,new Match("musicbrainz",id,row.optString("title"),row.optString("first-release-date"),"https://coverartarchive.org/release-group/"+id+"/front-500",term+" / "+clues.artist,score));}
        else {JSONArray releases=row.optJSONArray("releases");if(releases==null)continue;for(int j=0;j<releases.length();j++){JSONObject release=releases.getJSONObject(j),group=release.optJSONObject("release-group");if(group==null)continue;String id=group.optString("id");if(id.isEmpty())continue;String type=group.optString("primary-type"),secondary=group.optJSONArray("secondary-types")==null?"":group.getJSONArray("secondary-types").toString().toLowerCase(Locale.ROOT);double quality=ArtworkMatch.releaseQuality(type,secondary,release.optString("status"));
            put(candidates,new Match("musicbrainz",id,group.optString("title"),release.optString("date"),"https://coverartarchive.org/release-group/"+id+"/front-500",term+" / "+clues.artist,score+quality));
          }}
      }
      Match match=musicBest(candidates);if(match!=null){writeMatch(c,queryKey,match);return match;}
    }
    // A live set often has no catalogue recording. Use artwork from the identified
    // artist's closest release as a representative cover rather than leaving it blank.
    if(!clues.artist.isEmpty()){
      JSONArray rows=musicJson("https://musicbrainz.org/ws/2/release-group/?query="+enc("artist:"+quote(clues.artist))+"&fmt=json&limit=25").optJSONArray("release-groups");
      String sourceYear=ArtworkMatch.year(d.filename);
      if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);double ar=ArtworkMatch.score(clues.artist,artist(row));if(ar<.80)continue;String id=row.optString("id"),date=row.optString("first-release-date"),secondary=row.optJSONArray("secondary-types")==null?"":row.optJSONArray("secondary-types").toString();double score=ar+.2*ArtworkMatch.releaseQuality(row.optString("primary-type"),secondary,"Official")+.02*row.optDouble("score",0)/100;if(!sourceYear.isEmpty()&&date.matches("(?:19|20)\\d{2}.*"))score+=.05/(1+Math.abs(Integer.parseInt(sourceYear)-Integer.parseInt(date.substring(0,4))));put(candidates,new Match("musicbrainz",id,row.optString("title"),date,"https://coverartarchive.org/release-group/"+id+"/front-500",clues.artist+" (representative release)",score));}
    }
    Match fallback=musicBest(candidates);if(fallback!=null)writeMatch(c,queryKey,fallback);return fallback;
  }
  private static Match musicBest(Map<String,Match> candidates){List<Match> ranked=new ArrayList<>(candidates.values());ranked.sort((a,b)->{int score=Double.compare(b.score,a.score);return score==0?a.id.compareTo(b.id):score;});if(ranked.isEmpty()||ranked.get(0).score<.80)return null;Match selected=ranked.get(0);for(int i=1;i<Math.min(4,ranked.size());i++)if(ranked.get(i).score>=.80)selected.alternatives.add(ranked.get(i));return selected;}
  /** Independent public catalogue, including artist-release fallback for unlisted live sets. */
  private static Match deezer(Context c,Data d)throws Exception {
    ArtworkMatch.Music clues=ArtworkMatch.music(d.filename,d.title,d.artist,d.album);
    String term=clues.album.isEmpty()?clues.title:clues.album;if(term.isEmpty()&&clues.artist.isEmpty())return null;
    String queryKey="deezer1|"+clues.identity();Match previous=readMatch(c,queryKey);if(previous!=null)return previous;
    Map<String,Match> candidates=new HashMap<>();
    for(int attempt=0;attempt<(clues.artist.isEmpty()?1:2);attempt++){
      String query=attempt==0?(clues.artist+" "+term).trim():"artist:"+quote(clues.artist);
      long wait=1100-(System.currentTimeMillis()-lastDeezerRequest);if(wait>0)Thread.sleep(wait);lastDeezerRequest=System.currentTimeMillis();
      JSONObject response=json("https://api.deezer.com/search?q="+enc(query)+"&limit=25",null);if(response.has("error"))throw new IOException("Catalogue unavailable");JSONArray rows=response.optJSONArray("data");if(rows==null)continue;
      for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i),ar=row.optJSONObject("artist"),album=row.optJSONObject("album");if(ar==null||album==null)continue;
        String name=album.optString("title"),url=album.optString("cover_xl",album.optString("cover_big"));if(!safeMusicImage(url))continue;
        double titleScore=ArtworkMatch.score(term,clues.album.isEmpty()?row.optString("title"):name),artistScore=clues.artist.isEmpty()?titleScore:ArtworkMatch.score(clues.artist,ar.optString("name"));if(artistScore<.75)continue;
        double popularity=Math.min(1,row.optDouble("rank",0)/1000000.0),score;
        if(titleScore>=.65)score=.72*titleScore+.28*artistScore+.02*popularity;
        else if(attempt==1)score=.85*artistScore+.06*titleScore+.02*popularity;
        else continue;
        put(candidates,new Match("deezer",album.optString("id"),name,"",url,attempt==1&&titleScore<.65?clues.artist+" (representative release)":term+" / "+clues.artist,score));
      }
      Match match=musicBest(candidates);if(match!=null){writeMatch(c,queryKey,match);return match;}
    }return null;
  }
  private static boolean safeMusicImage(String url){try{return new URL(url).getProtocol().equals("https")&&new URL(url).getHost().equals("cdn-images.dzcdn.net");}catch(Exception ignored){return false;}}
  private static Match readMatch(Context c,String key){try{File file=new File(cache(c),"query-"+sha(key)+".json");if(!file.isFile())return null;JSONObject m=new JSONObject(read(file));if(System.currentTimeMillis()-m.optLong("saved")>=TTL)return null;String url=m.getString("url");if(!url.startsWith("https://image.tmdb.org/")&&!url.startsWith("https://coverartarchive.org/release-group/")&&!safeMusicImage(url)&&!safeYoutubeImage(url))return null;file.setLastModified(System.currentTimeMillis());return new Match(m.getString("provider"),m.getString("id"),m.getString("title"),m.optString("year"),url,m.getString("query"),m.getDouble("score"));}catch(Exception ignored){return null;}}
  private static void writeMatch(Context c,String key,Match m)throws Exception{JSONObject j=new JSONObject();j.put("provider",m.provider).put("id",m.id).put("title",m.title).put("year",m.year).put("url",m.url).put("query",m.query).put("score",m.score).put("saved",System.currentTimeMillis());write(new File(cache(c),"query-"+sha(key)+".json"),j.toString());}
  private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
  private static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
  private static String fuzzy(String s){StringBuilder b=new StringBuilder("(");for(String word:ArtworkMatch.normalize(s).split(" "))if(!word.isEmpty()){if(b.length()>1)b.append(" AND ");b.append(word).append(word.length()>=4?"~1":"");}return b.append(")").toString();}
  private static JSONObject musicJson(String url)throws Exception{long wait=1100-(System.currentTimeMillis()-lastMusicRequest);if(wait>0)Thread.sleep(wait);lastMusicRequest=System.currentTimeMillis();return json(url,null);}
  private static JSONObject json(String url,String bearer)throws Exception{return new JSONObject(new String(download(url,bearer,2*1024*1024),"UTF-8"));}
  private static byte[] download(String url,String bearer,int limit)throws Exception {
    for(int redirect=0;redirect<=4;redirect++){
      URL target=new URL(url);if(!target.getProtocol().equals("https"))throw new IOException("HTTPS required");
      HttpURLConnection connection=(HttpURLConnection)target.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(8000);connection.setRequestProperty("User-Agent",USER_AGENT);
      if(bearer!=null&&!bearer.isEmpty()&&target.getHost().equals("api.themoviedb.org"))connection.setRequestProperty("Authorization","Bearer "+bearer);
      try{int status=connection.getResponseCode();if(status>=300&&status<400){String next=connection.getHeaderField("Location");if(next==null)throw new IOException("Missing redirect");URL resolved=new URL(target,next);if(bearer!=null&&!resolved.getHost().equals("api.themoviedb.org"))throw new IOException("Auth redirect blocked");url=resolved.toString();continue;}if(status>=400)throw new HttpError(status);try(InputStream in=connection.getInputStream()){return bytes(in,limit);}}
      finally{connection.disconnect();}
    }
    throw new IOException("Redirect limit");
  }
  private static Bitmap bitmap(String url)throws Exception{try{return decode(download(url,null,8*1024*1024));}catch(HttpError missing){if(missing.status==404)return null;throw missing;}}
  private static Bitmap decodeFile(File file)throws Exception{if(!file.isFile()||file.length()>8*1024*1024)return null;try(InputStream in=new FileInputStream(file)){return decode(bytes(in,8*1024*1024));}}
  private static Bitmap decode(byte[] data){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);if(o.outWidth<=0||o.outHeight<=0||o.outWidth>12000||o.outHeight>12000)return null;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>720||o.outHeight/o.inSampleSize>1080)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(data,0,data.length,o);}
  private static byte[] bytes(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IOException("Response limit");out.write(buffer,0,n);}return out.toByteArray();}
  private static File cache(Context c){File f=new File(c.getCacheDir(),"premiere-art");f.mkdirs();return f;}
  private static String read(File f)throws Exception{try(InputStream in=new FileInputStream(f)){return new String(bytes(in,65536),"UTF-8");}}
  private static void write(File f,String s)throws Exception{File tmp=new File(f.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(s.getBytes("UTF-8"));out.getFD().sync();}if(!tmp.renameTo(f)){tmp.delete();throw new IOException("Atomic write failed");}}
  private static void saveBitmap(File f,Bitmap bitmap)throws Exception{File tmp=new File(f.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){bitmap.compress(Bitmap.CompressFormat.JPEG,88,out);out.getFD().sync();}if(!tmp.renameTo(f)){tmp.delete();throw new IOException("Atomic image write failed");}}
  private static String sha(String s){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format(Locale.ROOT,"%02x",v));return b.toString();}catch(Exception impossible){throw new IllegalStateException(impossible);}}
  private static void trim(File dir){File[] fs=dir.listFiles();if(fs==null)return;long total=0;int count=fs.length;for(File f:fs)total+=f.length();Arrays.sort(fs,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(File f:fs){if(System.currentTimeMillis()-f.lastModified()>TTL||total>64L*1024*1024||count>4096){long size=f.length();if(f.delete()){total-=size;count--;}}}}
  private Artwork(){}
}
