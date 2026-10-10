package org.videolan.vlc.premiere;
import java.util.*;
public final class ArtworkMatchTest {
  private static int count;
  public static void main(String[] args) {
    String mediaIdentity = ArtworkMatch.itemIdentity("file:///movie.mkv", "42", "Movie", "2024", 9000);
    require(mediaIdentity.equals(ArtworkMatch.itemIdentity("file:///movie.mkv", "42", "Movie", "2024", 9000)),
        "generated artwork MRL is outside stable media identity");
    require(!ArtworkMatch.itemIdentity("file:///movie.mkv", "42", "Movie", "2024", 9000)
        .equals(ArtworkMatch.itemIdentity("file:///movie.mkv", "42", "Movie", "2024", 9001)),
        "media length changes identity");
    require(ArtworkMatch.sameSnapshot(new String[] {"a", "b"}, new String[] {"a", "b"}),
        "unchanged library snapshot");
    require(!ArtworkMatch.sameSnapshot(new String[] {"a", "b"}, new String[] {"a", "c"}),
        "changed library item refreshes snapshot");
    require(!ArtworkMatch.sameSnapshot(new String[] {"a"}, new String[] {"a", "b"}),
        "new library item refreshes snapshot");
    require(ArtworkMatch.normalize("Amélie — 2001").equals("amelie 2001"), "Unicode");
    require(ArtworkMatch.uniqueStrong("Blade Runner","1982",Arrays.asList(new String[][]{{"Blade Runner","1982"},{"Blade Runner","2017"}})),"wrong-year excluded");
    require(!ArtworkMatch.uniqueStrong("Crash","",Arrays.asList(new String[][]{{"Crash","1996"},{"Crash","2004"}})),"ambiguous remakes");
    require(ArtworkMatch.score("Spider-Man 2","Spider Man 2")==1,"punctuation");
    require(ArtworkMatch.score("Spider-Man 2","Spider-Man 3")==0,"numeric sequel conflict");
    require(ArtworkMatch.score("Organ Donr","Organ Donor")>=.90,"small spelling error");
    require(ArtworkMatch.score("DJ Shadow","DJ Shadwo")>=.75,"typo artist plausibility");
    require(ArtworkMatch.year("2001 A Space Odyssey.1080p").isEmpty(),"numeric title not year");
    require(ArtworkMatch.videoTitle("2001.A.Space.Odyssey.1968.1080p.mkv").equals("2001 A Space Odyssey"),"numeric title preserved with year");
    require(ArtworkMatch.videoTitle("1917.2019.1080p.mkv").equals("1917"),"numeric movie");
    require(ArtworkMatch.videoTitle("Project.Hail.Mary.2026.IMAX.1080p.WEBRip.x264.AAC5.1-[YTS].mp4").equals("Project Hail Mary"),"actual movie filename");
    require(ArtworkMatch.videoTitle("Spider Man Brand New Day 2026 AAC5 1").equals("Spider Man Brand New Day"),"VLC metadata noise");
    require(ArtworkMatch.videoTitle("Lanterns.2026.S01E08.1080p.WEB.h264-ETHEL.mkv").equals("Lanterns"),"episode filename");
    require(ArtworkMatch.episode("Series S01 E08.mkv"),"episode marker");
    require(ArtworkMatch.camera("VID_20261006_191421730.mp4"),"camera bypass");
    music("001 - DJ shadow   Organ Donor.mp3","","","","Organ Donor","DJ shadow",false);
    music("01. DJ Shadow - Organ Donor.mp3","","","","Organ Donor","DJ Shadow",false);
    music("1-03 - Massive Attack - Teardrop.flac","","","","Teardrop","Massive Attack",false);
    music("02 - Organ Donor.mp3","","DJ Shadow","Endtroducing.....","Organ Donor","DJ Shadow",false);
    music("01 - Björk - Jóga.mp3","Jóga","Björk","Homogenic","Jóga","Björk",false);
    music("001 - DJ shadow   Organ Donor.mp3","001 - DJ shadow   Organ Donor","","","Organ Donor","DJ shadow",false);
    music("001 - DJ shadow   Organ Donor.mp3","001 - DJ shadow   Organ Donor.mp3","","","Organ Donor","DJ shadow",false);
    music("026 - Daft Punk - Rollin' & Scratchin' (Official Audio).mp3","026 - Daft Punk - Rollin' & Scratchin' (Official Audio).mp3","","Music","Rollin' & Scratchin'","Daft Punk",false);
    music("353 - Daft Punk - Burnin' (Official Audio).mp3","353 - Daft Punk - Burnin' (Official Audio).mp3","","","Burnin'","Daft Punk",false);
    music("351 - N.O.R.E. - Superthug (Official Music Video).mp3","351 - N.O.R.E. - Superthug (Official Music Video).mp3","","","Superthug","N.O.R.E.",false);
    music("023 - Daft Punk Live @ Even Further 26 05 1996 [Remake] [HQ].mp3","023 - Daft Punk Live @ Even Further 26 05 1996 [Remake] [HQ].mp3","","","Live @ Even Further 26 05 1996 [Remake] [HQ]","Daft Punk",false);
    music("01 - Artist - A Song - Extended Mix.mp3","","","","A Song - Extended Mix","Artist",false);
    music("002 - Klangkuenstler ⧸ December 25 ⧸ 4pm-5pm.mp3","002 - Klangkuenstler ⧸ December 25 ⧸ 4pm-5pm.mp3","","","December 25 ⧸ 4pm-5pm","Klangkuenstler",false);
    music("026 - Daft Punk - Rollin' & Scratchin' (Official Audio).mp3","","","","Rollin' & Scratchin'","Daft Punk",false);
    music("026 - Daft Punk - Rollin' & Scratchin' (Official Audio).mp3","026 - Daft Punk - Rollin' & Scratchin' (Official Audio)","","","Rollin' & Scratchin'","Daft Punk",false);
    music("Daft Punk - Rollin' & Scratchin' (Official Audio).mp3","Daft Punk - Rollin' & Scratchin' (Official Audio)","","","Rollin' & Scratchin'","Daft Punk",false);
    music("Daft Punk - Alive (Live).mp3","","","","Alive (Live)","Daft Punk",false);
    music("Daft Punk - One More Time (Radio Remix) [Official Video].mp3","","","","One More Time (Radio Remix)","Daft Punk",false);
    music("001 - DJ Shadow - Organ Donor.mp3","Teardrop","Massive Attack","Mezzanine","Teardrop","Massive Attack",true);
    music("01 - Organ Donor (extended overhaul).mp3","","DJ Shadow","","Organ Donor (extended overhaul)","DJ Shadow",false);
    music("007 - Organ Donor.mp3","","","","Organ Donor","",false);
    music("Teardrop.mp3","Teardrop","Massive Attack","Mezzanine","Teardrop","Massive Attack",false);
    music("001 - DJ Shadow - Organ Donor.mp3","","","Music","Organ Donor","DJ Shadow",false);
    require(ArtworkMatch.music("001 - DJ Shadow - Organ Donor.mp3","","","Music").album.isEmpty(),"generic system album ignored");
    require(ArtworkMatch.music("001 - Unidentified Track.mp3","","","").artist.isEmpty(),"no invented artist");
    require(ArtworkMatch.releaseQuality("Album","[]","Official")-ArtworkMatch.releaseQuality("Album","[Soundtrack]","Official")>=.08,"studio album preferred to soundtrack");
    require(ArtworkMatch.releaseQuality("Album","[]","Official")-ArtworkMatch.releaseQuality("Single","[]","Official")>=.08,"studio album preferred to standalone single");
    require(ArtworkMatch.releaseQuality("Album","[Live]","Bootleg")<0,"bootleg/live penalty");
    require(ArtworkMatch.liveSet("Klangkuenstler / December 25 / 4pm-5pm"),"DJ set qualifier detected");
    ArtworkMatch.Music set=ArtworkMatch.music("002 - Klangkuenstler ⧸ December 25 ⧸ 4pm-5pm.mp3","002 - Klangkuenstler ⧸ December 25 ⧸ 4pm-5pm.mp3","","");
    require(ArtworkMatch.youtubeScore(set.artist,set.title,"Klangkuenstler - December 25 4pm-5pm")>=.74,"real DJ set filename match");
    require(ArtworkMatch.youtubeScore("Klangkuenstler","December 25 4pm-5pm","Random Artist - December 25 4pm-5pm")<0,"wrong artist rejected");
    require(ArtworkMatch.youtubeId("https://www.youtube.com/watch?v=abcdefghijk&t=30").equals("abcdefghijk"),"YouTube URL video id");
    require(ArtworkMatch.youtubeId("abcdefghijk").equals("abcdefghijk"),"standalone video id");
    require(ArtworkMatch.youtubeId("not-a-video-id").isEmpty(),"invalid video id rejected");
    require(ArtworkMatch.youtubeId("A song [abcdefghijk].mp3").equals("abcdefghijk"),"bracketed source id");
    require(ArtworkMatch.youtubeId("Celebration - A Song.mp3").isEmpty(),"ordinary eleven letter word is not a video id");
    require(ArtworkMatch.youtubeScore("Massive Attack","Teardrp","Massive Attack - Teardrop (Official Music Video)")>.85,"fuzzy song thumbnail beats unrelated artist release");
    require(ArtworkMatch.youtubeScore("Daft Punk","Around the World","Around the World - Daft Punk (Official Audio)")>.90,"artist follows track in upload title");
    require(ArtworkMatch.youtubeScore("","Teardrop","Teardrop")>.80,"title-only source matching");
    String sample="prefix {\"ignored\":\"brace } and escaped \\\" quote\",\"nested\":{\"ok\":true}} suffix";
    int objectStart=sample.indexOf('{');
    require(ArtworkMatch.balancedObject(sample,objectStart).equals("{\"ignored\":\"brace } and escaped \\\" quote\",\"nested\":{\"ok\":true}}"),"balanced JSON handles braces in strings");
    require(ArtworkMatch.balancedObject("{ broken",0).isEmpty(),"unterminated JSON rejected");
    System.out.println(count+" artwork matching assertions passed");
  }
  static void music(String f,String t,String ar,String al,String expectedTitle,String expectedArtist,boolean conflict){ArtworkMatch.Music m=ArtworkMatch.music(f,t,ar,al);require(m.title.equals(expectedTitle)&&m.artist.equals(expectedArtist)&&m.conflict==conflict,"filename/tag fusion: "+f);}
  static void require(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
}
