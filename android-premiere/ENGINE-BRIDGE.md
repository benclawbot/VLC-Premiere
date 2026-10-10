# Native engine bridge references

Signatures extracted from the original decoded smali. The new native UI may call these directly or through reflection while retaining VLC's engine.

## org/videolan/medialibrary/interfaces/Medialibrary

Source: `smali/smali_classes5/org/videolan/medialibrary/interfaces/Medialibrary.smali`

```smali
.method public static getInstance()Lorg/videolan/medialibrary/interfaces/Medialibrary;
.method public abstract createPlaylist(Ljava/lang/String;ZZ)Lorg/videolan/medialibrary/interfaces/media/Playlist;
.method public abstract discover(Ljava/lang/String;)V
.method public abstract getAlbums(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/Album;
.method public abstract getAlbums(ZZ)[Lorg/videolan/medialibrary/interfaces/media/Album;
.method public abstract getArtists(ZIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Artist;
.method public abstract getArtists(ZZZ)[Lorg/videolan/medialibrary/interfaces/media/Artist;
.method public abstract getAudio()[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract getAudio(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract getGenres(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/Genre;
.method public abstract getGenres(ZZ)[Lorg/videolan/medialibrary/interfaces/media/Genre;
.method public abstract getMedia(J)Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract getMedia(Landroid/net/Uri;)Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract getMedia(Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract getPlaylists(Lorg/videolan/medialibrary/interfaces/media/Playlist$Type;IZZZ)[Lorg/videolan/medialibrary/interfaces/media/Playlist;
.method public abstract getPlaylists(Lorg/videolan/medialibrary/interfaces/media/Playlist$Type;Z)[Lorg/videolan/medialibrary/interfaces/media/Playlist;
.method public abstract getRecentVideos()[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract getVideos()[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract getVideos(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public abstract history(I)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public isInitiated()Z
.method public isStarted()Z
.method public abstract reload()V
.method public abstract reload(Ljava/lang/String;)V
```

## org/videolan/medialibrary/interfaces/media/MediaWrapper

Source: `smali/smali_classes5/org/videolan/medialibrary/interfaces/media/MediaWrapper.smali`

```smali
.method public abstract getAlbum()Lorg/videolan/medialibrary/interfaces/media/Album;
.method public abstract getArtist()Lorg/videolan/medialibrary/interfaces/media/Artist;
.method public getFileName()Ljava/lang/String;
.method public getGenre()Ljava/lang/String;
.method public getHeight()I
.method public getLength()J
.method public getTime()J
.method public getTitle()Ljava/lang/String;
.method public getType()I
.method public getUri()Landroid/net/Uri;
.method public getWidth()I
```

## org/videolan/medialibrary/interfaces/media/Playlist

Source: `smali/smali_classes5/org/videolan/medialibrary/interfaces/media/Playlist.smali`

```smali
.method public abstract append(J)Z
.method public abstract append(Ljava/util/List;)Z
.method public abstract append([J)Z
.method public abstract delete()Z
.method public abstract getTracks(ZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public getTracksCount()I
.method public abstract move(II)Z
.method public abstract remove(I)Z
```

## org/videolan/vlc/PlaybackService

Source: `smali/smali_classes6/org/videolan/vlc/PlaybackService.smali`

```smali
.method private final insertNext(Ljava/util/List;)V
.method public final append(Ljava/util/List;I)Lkotlinx/coroutines/Job;
.method public final append(Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)Lkotlinx/coroutines/Job;
.method public final append([Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;I)Lkotlinx/coroutines/Job;
.method public final getCurrentMediaWrapper()Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;
.method public final getLength()J
.method public final getRate()F
.method public final getTime(J)I
.method public final getTime()J
.method public final insertNext(Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V
.method public final insertNext([Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V
.method public final isPlaying()Z
.method public final isShuffling()Z
.method public final load(Ljava/util/List;I)Lkotlinx/coroutines/Job;
.method public final load(Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)Lkotlinx/coroutines/Job;
.method public final load([Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;I)V
.method public final next(Z)V
.method public final pause()V
.method public final play()V
.method public final previous(Z)V
.method public final resetRate()V
.method public final setRate(FZ)V
.method public final setTime(JZ)V
.method public final shuffle()V
.method public final stop()V
.method public final stop(Z)V
.method public final stop(ZZ)V
```

## org/videolan/vlc/PlaybackService$Companion

Source: `smali/smali_classes6/org/videolan/vlc/PlaybackService$Companion.smali`

```smali
.method public final getInstance()Lorg/videolan/vlc/PlaybackService;
```

## org/videolan/vlc/media/MediaUtils

Source: `smali/smali_classes6/org/videolan/vlc/media/MediaUtils.smali`

```smali
.method public final appendMedia(Landroid/content/Context;Ljava/util/List;)V
.method public final appendMedia(Landroid/content/Context;Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V
.method public final appendMedia(Landroid/content/Context;[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V
.method public final insertNext(Landroid/content/Context;Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V
.method public final insertNext(Landroid/content/Context;[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V
.method public final openList(Landroid/content/Context;Ljava/util/List;I)V
.method public final openList(Landroid/content/Context;Ljava/util/List;IZ)V
.method public final openMedia(Landroid/content/Context;Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V
.method public final openPlaylist(Landroid/content/Context;J)V
.method public final openPlaylist(Landroid/content/Context;JI)V
.method public final openPlaylist(Landroid/content/Context;JIZ)V
.method public final openUri(Landroid/content/Context;Landroid/net/Uri;)V
```

## org/videolan/vlc/gui/video/VideoPlayerActivity

Source: `smali/smali_classes6/org/videolan/vlc/gui/video/VideoPlayerActivity.smali`

```smali
.method public final getService()Lorg/videolan/vlc/PlaybackService;
.method protected onCreate(Landroid/os/Bundle;)V
```

## org/videolan/vlc/gui/MainActivity

Source: `smali/smali_classes6/org/videolan/vlc/gui/MainActivity.smali`

```smali
.method protected onCreate(Landroid/os/Bundle;)V
```
