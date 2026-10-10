package org.videolan.vlc.premiere;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Filename/tag fusion and conservative fuzzy ranking; also exercised by JVM fixtures. */
final class ArtworkMatch {
  /** Stable library identity; artwork MRLs are generated asynchronously by VLC. */
  static String itemIdentity(String uri, String id, String title, String date, long length) {
    return String.valueOf(uri) + ":" + String.valueOf(id) + ":" + String.valueOf(title)
        + ":" + String.valueOf(date) + ":" + length;
  }
  static boolean sameSnapshot(String[] before, String[] after) {
    return Arrays.equals(before == null ? new String[0] : before,
        after == null ? new String[0] : after);
  }
  static String normalize(String value) {
    String s = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    return s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
  }
  static String known(String s) {
    if(s==null) return "";
    String n=normalize(s);
    return n.isEmpty() || n.equals("null") || n.equals("unknown") || n.startsWith("unknown ") || n.equals("untitled") ? "" : s.trim();
  }
  static String stem(String s) {
    return (s==null?"":s).replaceAll("(?i)\\.(mkv|mp4|avi|mov|m4v|webm|mpg|mpeg|ts|m2ts|mp3|flac|m4a|ogg|opus|wav|aac|wma)$", "");
  }
  static String year(String s) {
    Matcher m=Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)").matcher(s==null?"":s);
    String y="";
    while(m.find()) if(m.start()>0 && !normalize(s.substring(0,m.start())).isEmpty()) y=m.group(1);
    return y;
  }
  static boolean episode(String s) { return Pattern.compile("(?i)(?:S\\d{1,2}[ ._-]*E\\d{1,3}|\\b\\d{1,2}x\\d{1,3}\\b)").matcher(s==null?"":s).find(); }
  static String videoTitle(String value) {
    String s=stem(value);
    s=s.replaceAll("(?i)[ ._-]+(?:S\\d{1,2}[ ._-]*E\\d{1,3}|\\d{1,2}x\\d{1,3}).*$", "");
    s=s.replaceAll("(?i)\\b(?:2160p|1080p|720p|480p|4k|8k|uhd|hdr10?\\+?|web[ ._-]?(?:dl|rip)|bluray|brrip|hdtv|x26[45]|h[ .]?26[45]|hevc|av1|aac[0-9 .]*|dts|proper|repack|remux|imax)\\b.*$", "");
    s=s.replaceAll("\\[[^]]*\\]", " ");
    s=s.replaceAll("[._]+", " ").replaceAll("\\s+", " ").trim();
    // Only a suffix year is a release clue. Leading numeric titles remain intact.
    s=s.replaceAll("[ (]+(?:19|20)\\d{2}\\)?$", "").trim();
    return s;
  }
  static boolean camera(String s) { return stem(s).matches("(?i)(VID|IMG|MOV|PXL)[_-]?\\d{8}.*"); }
  static Set<String> numbers(String s) {
    Set<String> out=new HashSet<>(); for(String x:s.split(" ")) if(x.matches("\\d+"))out.add(x);return out;
  }
  static double score(String a,String b) {
    String x=normalize(a),y=normalize(b); if(x.isEmpty()||y.isEmpty())return 0; if(x.equals(y))return 1;
    if(!numbers(x).equals(numbers(y)))return 0;
    double ordered=1-(double)distance(x,y)/Math.max(x.length(),y.length());
    String[] xs=x.split(" "),ys=y.split(" ");
    double sum=0; boolean[] used=new boolean[ys.length];
    for(String token:xs){double best=0;int at=-1;for(int j=0;j<ys.length;j++)if(!used[j]){double v=1-(double)distance(token,ys[j])/Math.max(token.length(),ys[j].length());if(v>best){best=v;at=j;}}if(at>=0)used[at]=true;sum+=best;}
    double tokens=sum/Math.max(xs.length,ys.length);
    return Math.max(ordered, tokens*.97);
  }
  private static int distance(String x,String y) {
    if(x.length()>256)x=x.substring(0,256);if(y.length()>256)y=y.substring(0,256);
    int[] row=new int[y.length()+1];for(int j=0;j<row.length;j++)row[j]=j;
    for(int i=1;i<=x.length();i++){int diagonal=row[0];row[0]=i;for(int j=1;j<=y.length();j++){int old=row[j];row[j]=Math.min(Math.min(row[j]+1,row[j-1]+1),diagonal+(x.charAt(i-1)==y.charAt(j-1)?0:1));diagonal=old;}}
    return row[y.length()];
  }
  static final class Music {
    final String title,artist,album; final boolean conflict;
    Music(String t,String ar,String al,boolean c){title=t;artist=ar;album=al;conflict=c;}
    String identity(){return normalize(artist)+"|"+normalize(album.isEmpty()?title:album);}
  }
  static String unnumber(String s) {
    return stem(s).replaceFirst("^\\s*(?:(?:CD|DISC)[ ._-]*\\d+[ ._-]+)?(?:\\d{1,2}[-.]\\d{2,3}|\\d{1,3})[ ._-]+", "").trim();
  }
  /** Remove common uploader suffixes while preserving meaningful remix/live qualifiers. */
  static String musicTitle(String s) {
    return (s==null?"":s).replaceAll("(?i)\\s*(?:[-–—]+\\s*)?(?:\\[(?:official\\s+(?:audio|(?:music\\s+)?video)|lyrics?|visuali[sz]er|audio only)\\]|\\((?:official\\s+(?:audio|(?:music\\s+)?video)|lyrics?|visuali[sz]er|audio only)\\)|(?:official\\s+(?:audio|(?:music\\s+)?video)|lyrics?|visuali[sz]er))\\s*$", "").trim();
  }
  static boolean liveSet(String s) { return Pattern.compile("(?i)\\b(?:live|dj\\s*set|boiler\\s*room)\\b|\\b\\d{1,2}(?:am|pm)[-–/]\\d{1,2}(?:am|pm)\\b").matcher(s==null?"":s).find(); }
  static String youtubeId(String s) {
    if(s==null)return "";
    Matcher m=Pattern.compile("(?i)(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#\\s]*&)?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})").matcher(s);
    if(m.find())return m.group(1);
    m=Pattern.compile("\\[([A-Za-z0-9_-]{11})\\]").matcher(s);
    if(m.find())return m.group(1);
    String candidate=stem(s.trim());return candidate.matches("[A-Za-z0-9_-]{11}")?candidate:"";
  }
  static double youtubeScore(String artist,String title,String candidate) {
    String a=normalize(artist),c=normalize(musicTitle(candidate)),remaining=c;
    if(c.isEmpty())return -1;
    if(a.isEmpty()){double similarity=score(title,c);return similarity>=.80?similarity:-1;}
    String[] words=c.split(" ");int width=a.split(" ").length,bestAt=-1;double artistScore=0;
    for(int i=0;i+width<=words.length;i++){StringBuilder segment=new StringBuilder();for(int j=i;j<i+width;j++){if(segment.length()>0)segment.append(' ');segment.append(words[j]);}double similarity=score(a,segment.toString());if(similarity>artistScore){artistScore=similarity;bestAt=i;}}
    if(artistScore<.80)return -1;
    StringBuilder rest=new StringBuilder();for(int i=0;i<words.length;i++)if(i<bestAt||i>=bestAt+width){if(rest.length()>0)rest.append(' ');rest.append(words[i]);}remaining=rest.toString();
    double titleScore=score(title,remaining);
    return titleScore<.62?-1:.72*titleScore+.28*artistScore;
  }
  /** Extract a single balanced JSON object without interpreting JavaScript. */
  static String balancedObject(String text,int start) {
    if(text==null||start<0||start>=text.length()||text.charAt(start)!='{')return "";
    int depth=0;boolean string=false,escape=false;
    for(int i=start;i<text.length();i++){char c=text.charAt(i);if(string){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')string=false;continue;}if(c=='"'){string=true;continue;}if(c=='{'){if(++depth>64)return "";}else if(c=='}'&&--depth==0)return text.substring(start,i+1);}
    return "";
  }
  static Music music(String filename,String tagTitle,String tagArtist,String tagAlbum) {
    String raw=unnumber(filename),ft="",fa="";
    // Spaces around a dash or multiple spaces are separators; artist hyphens are retained.
    String[] pieces=raw.split("\\s+[–—/⧸|\\-]\\s+|\\s{2,}", 2);
    if(pieces.length>=2){fa=pieces[0].trim();ft=musicTitle(pieces[pieces.length-1].trim());}
    if(fa.isEmpty()){Matcher live=Pattern.compile("^(.+?)\\s+((?:Live|DJ Set|Boiler Room)\\b.*|@\\s+.+)$",Pattern.CASE_INSENSITIVE).matcher(raw);if(live.matches()){fa=live.group(1).trim();ft=live.group(2).trim();}}
    String artist=known(tagArtist),title=musicTitle(stem(known(tagTitle))),album=known(tagAlbum);
    // VLC can expose the media-library folder name as an album tag for every
    // imported track. It is not release metadata and must not outrank filename clues.
    String albumName=normalize(album);
    if(albumName.equals("music")||albumName.equals("audio")||albumName.equals("media")
        ||albumName.equals("downloads")||albumName.equals("unknown album"))album="";
    if(!artist.isEmpty() && ft.isEmpty()) {
      String n=normalize(raw),ar=normalize(artist);
      if(n.startsWith(ar+" ")){int words=ar.split(" ").length;String[] parts=raw.split("\\s+",words+1);if(parts.length>words)ft=parts[words];fa=artist;}
      else ft=raw;
    }
    if(ft.isEmpty())ft=musicTitle(raw);
    // VLC commonly exposes a filename as title. Do not treat that as a conflicting tag.
    // Some MediaLibrary versions return the complete filename (including its
    // track prefix) as the title tag. Compare against both the parsed stem and
    // the original filename before deciding that a useful title conflicts.
    String filenameStem=stem(filename);
    if(normalize(title).equals(normalize(raw)) || normalize(title).equals(normalize(stem(raw)))
        || normalize(title).equals(normalize(filenameStem))
        || normalize(title).equals(normalize(musicTitle(filenameStem))))title="";
    boolean conflict=!artist.isEmpty()&&!fa.isEmpty()&&score(artist,fa)<.70;
    if(!title.isEmpty()&&!ft.isEmpty()&&score(title,ft)<.70) conflict=true;
    if(artist.isEmpty())artist=fa;
    if(title.isEmpty())title=ft.isEmpty()?raw:ft;
    return new Music(title,artist,album,conflict);
  }
  static double releaseQuality(String primary,String secondary,String status) {
    double value="Album".equals(primary)?.18:"Single".equals(primary)?.06:0;
    String types=secondary==null?"":secondary.toLowerCase(Locale.ROOT);
    if(!types.isEmpty()&&!types.equals("[]"))value-=.15;
    if(types.contains("live")||"Bootleg".equals(status))value-=.30;
    return value;
  }
  static boolean uniqueStrong(String title,String year,List<String[]> results) {
    double best=0,second=0;
    for(String[] r:results){String cy=r.length>1?r[1]:"";if(year!=null&&!year.isEmpty()&&!cy.isEmpty()&&!year.equals(cy))continue;double v=score(title,r[0]);if(v>best){second=best;best=v;}else if(v>second)second=v;}
    return best>=.88&&best-second>=.08;
  }
  private ArtworkMatch() {}
}
