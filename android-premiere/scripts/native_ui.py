#!/usr/bin/env python3
"""Compile the native presentation and hook VLC's existing application lifecycle."""
from pathlib import Path
import subprocess,sys,zipfile,shutil,hashlib,json
sys.path.insert(0, str(Path(__file__).resolve().parent))
from toolchain import java_tool

root=Path(__file__).resolve().parents[2]
base=root/'android-premiere'
work=base/'work'
mode=sys.argv[1]
if mode=='prepare':
    # The original split snapshot and full upstream APK arrange DEX files
    # differently. Resolve the startup class by its package in either tree.
    application_class='org/videolan/mobile/app/VLCApplication.smali'
    candidates=[tree/application_class for tree in work.glob('smali*')
                if (tree/application_class).is_file()]
    assert len(candidates)==1, 'Expected one decoded VLCApplication class'
    app=candidates[0]
    text=app.read_text()
    anchor='    invoke-super {p0}, Landroidx/multidex/MultiDexApplication;->onCreate()V'
    assert anchor in text
    text=text.replace(anchor,anchor+'\n\n    invoke-static {p0}, Lorg/videolan/vlc/premiere/Premiere;->boot(Landroid/app/Application;)V',1)
    app.write_text(text)
    import shutil
    for name in ['golden-hour','alpine','coast']:
        destination=work/'assets/premiere'/f'{name}.jpg'
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(base/'assets/premiere'/f'{name}.jpg',destination)
    shutil.copy2(base/'assets/premiere/tmdb-logo.png',work/'assets/premiere/tmdb-logo.png')
    main=work/'smali_classes6/org/videolan/vlc/gui/MainActivity.smali'
    text=main.read_text()
    assert '.method public onBackPressed()V' not in text
    # MainActivity extends AudioPlayerContainerActivity. Let its original back
    # dispatcher handle the root; the new screen stack consumes detail navigation.
    import re
    parent=re.search(r'^\.super (L[^;]+;)',text,re.M).group(1)
    text += f'''\n.method public onBackPressed()V
    .locals 1
    invoke-static {{p0}}, Lorg/videolan/vlc/premiere/Premiere;->back(Landroid/app/Activity;)Z
    move-result v0
    if-nez v0, :premiere_back_done
    invoke-super {{p0}}, {parent}->onBackPressed()V
    :premiere_back_done
    return-void
.end method
'''
    main.write_text(text)
    # Register after the native activity callbacks so gestures reach Premiere's
    # actual screen stack. Lifecycle ownership removes the callback on destroy;
    # a root-screen back delegates to the original dispatcher without recursion.
    bridge=work/'smali_classes6/org/videolan/vlc/premiere/PremiereBackCallback.smali'
    bridge.parent.mkdir(parents=True,exist_ok=True)
    bridge.write_text(''' .class public final Lorg/videolan/vlc/premiere/PremiereBackCallback;
.super Landroidx/activity/OnBackPressedCallback;

.field private final activity:Landroidx/activity/ComponentActivity;

.method private constructor <init>(Landroidx/activity/ComponentActivity;)V
    .locals 1
    const/4 v0, 0x1
    invoke-direct {p0, v0}, Landroidx/activity/OnBackPressedCallback;-><init>(Z)V
    iput-object p1, p0, Lorg/videolan/vlc/premiere/PremiereBackCallback;->activity:Landroidx/activity/ComponentActivity;
    return-void
.end method

.method public static register(Landroid/app/Activity;)V
    .locals 2
    instance-of v0, p0, Landroidx/activity/ComponentActivity;
    if-eqz v0, :done
    check-cast p0, Landroidx/activity/ComponentActivity;
    new-instance v0, Lorg/videolan/vlc/premiere/PremiereBackCallback;
    invoke-direct {v0, p0}, Lorg/videolan/vlc/premiere/PremiereBackCallback;-><init>(Landroidx/activity/ComponentActivity;)V
    invoke-virtual {p0}, Landroidx/activity/ComponentActivity;->getOnBackPressedDispatcher()Landroidx/activity/OnBackPressedDispatcher;
    move-result-object v1
    invoke-virtual {v1, p0, v0}, Landroidx/activity/OnBackPressedDispatcher;->addCallback(Landroidx/lifecycle/LifecycleOwner;Landroidx/activity/OnBackPressedCallback;)V
    :done
    return-void
.end method

.method public handleOnBackPressed()V
    .locals 2
    iget-object v0, p0, Lorg/videolan/vlc/premiere/PremiereBackCallback;->activity:Landroidx/activity/ComponentActivity;
    invoke-static {v0}, Lorg/videolan/vlc/premiere/Premiere;->back(Landroid/app/Activity;)Z
    move-result v1
    if-nez v1, :done
    const/4 v1, 0x0
    invoke-virtual {p0, v1}, Landroidx/activity/OnBackPressedCallback;->setEnabled(Z)V
    invoke-virtual {v0}, Landroidx/activity/ComponentActivity;->getOnBackPressedDispatcher()Landroidx/activity/OnBackPressedDispatcher;
    move-result-object v0
    invoke-virtual {v0}, Landroidx/activity/OnBackPressedDispatcher;->onBackPressed()V
    const/4 v1, 0x1
    invoke-virtual {p0, v1}, Landroidx/activity/OnBackPressedCallback;->setEnabled(Z)V
    :done
    return-void
.end method
'''.lstrip())
    preference=work/'smali_classes6/org/videolan/vlc/gui/preferences/PreferencesActivity.smali'
    preference_text=preference.read_text()
    assert '.method public onBackPressed()V' not in preference_text
    parent=re.search(r'^\.super (L[^;]+;)',preference_text,re.M).group(1)
    preference_text += f'''
.method public onBackPressed()V
    .locals 1
    invoke-static {{p0}}, Lorg/videolan/vlc/premiere/Premiere;->back(Landroid/app/Activity;)Z
    move-result v0
    if-nez v0, :premiere_pref_done
    invoke-super {{p0}}, {parent}->onBackPressed()V
    :premiere_pref_done
    return-void
.end method
'''
    preference.write_text(preference_text)
    # Premiere replaces the controls demonstrated by VLC's native first-play
    # tutorial. Initializing the covered tutorial also pauses every Playing
    # event, leaving the visible player permanently paused. Skip that tutorial
    # in this presentation without changing the user's tutorial preference.
    tips=work/'smali_classes6/org/videolan/vlc/gui/video/VideoTipsDelegate.smali'
    tips_text=tips.read_text()
    tip_start=tips_text.index('.method public final init()V')
    tip_end=tips_text.index('.end method',tip_start)+len('.end method')
    tips_text=tips_text[:tip_start]+''' .method public final init()V
    .locals 0
    return-void
.end method'''.lstrip()+tips_text[tip_end:]
    tips.write_text(tips_text)
    # The native audio/playlist guide is covered by Premiere as well. Its
    # showTipViewIfNeeded() pauses the service even when no guide is visible.
    # Skip the obsolete presentation without marking user tips as completed.
    audio_container=work/'smali_classes6/org/videolan/vlc/gui/AudioPlayerContainerActivity.smali'
    audio_text=audio_container.read_text()
    audio_tip_sig='.method public final showTipViewIfNeeded(ILjava/lang/String;)V'
    audio_start=audio_text.index(audio_tip_sig)
    audio_end=audio_text.index('.end method',audio_start)+len('.end method')
    audio_container.write_text(audio_text[:audio_start]+audio_tip_sig+'''
    .locals 0
    return-void
.end method'''+audio_text[audio_end:])
    # Bridge Android document-picker results without replacing VLC's handlers.
    for activity in [main,work/'smali_classes6/org/videolan/vlc/gui/video/VideoPlayerActivity.smali']:
        body=activity.read_text()
        sig='.method protected onActivityResult(IILandroid/content/Intent;)V'
        start=body.index(sig)
        pos=body.index('\n',body.index('.locals ',start))
        body=body[:pos]+"\n\n    invoke-static/range {p0 .. p3}, Lorg/videolan/vlc/premiere/Premiere;->result(Landroid/app/Activity;IILandroid/content/Intent;)V"+body[pos:]
        activity.write_text(body)

    classes=base/'build/native-classes'
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True,exist_ok=True)
    sources=sorted((base/'native').rglob('*.java'))
    subprocess.run([str(java_tool('javac')),'-source','8','-target','8','-classpath',str(base/'tools/android.jar'),'-d',str(classes),*[str(s) for s in sources]],check=True)
    jar=base/'build/native-ui.jar'
    with zipfile.ZipFile(jar,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,str(f.relative_to(classes)))
    dex=base/'build/native-dex';dex.mkdir(exist_ok=True)
    subprocess.run([str(java_tool('java')),'-cp',str(base/'tools/build-tools/android-16/lib/d8.jar'),'com.android.tools.r8.D8','--min-api','30','--lib',str(base/'tools/android.jar'),'--output',str(dex),str(jar)],check=True)
    source_hash=hashlib.sha256()
    for path in sources:
        source_hash.update(str(path.relative_to(base/'native')).encode())
        source_hash.update(path.read_bytes())
    (base/'build/native-source-manifest.json').write_text(json.dumps({'sources_sha256':source_hash.hexdigest(),'dex_sha256':hashlib.sha256((dex/'classes.dex').read_bytes()).hexdigest()},indent=2)+'\n')
    print('Native Java UI compiled; application and back-stack hooks prepared')
elif mode=='refresh':
    classes=base/'build/native-classes'
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True,exist_ok=True)
    sources=sorted((base/'native').rglob('*.java'))
    subprocess.run([str(java_tool('javac')),'-source','8','-target','8','-classpath',str(base/'tools/android.jar'),'-d',str(classes),*[str(s) for s in sources]],check=True)
    jar=base/'build/native-ui.jar'
    with zipfile.ZipFile(jar,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,str(f.relative_to(classes)))
    dex=base/'build/native-dex';dex.mkdir(exist_ok=True)
    subprocess.run([str(java_tool('java')),'-cp',str(base/'tools/build-tools/android-16/lib/d8.jar'),'com.android.tools.r8.D8','--min-api','30','--lib',str(base/'tools/android.jar'),'--output',str(dex),str(jar)],check=True)
    source_hash=hashlib.sha256()
    for path in sources:
        source_hash.update(str(path.relative_to(base/'native')).encode())
        source_hash.update(path.read_bytes())
    (base/'build/native-source-manifest.json').write_text(json.dumps({'sources_sha256':source_hash.hexdigest(),'dex_sha256':hashlib.sha256((dex/'classes.dex').read_bytes()).hexdigest()},indent=2)+'\n')
    print('Native Java UI compiled; application and back-stack hooks prepared')
    apk=base/'dist/VLC-Premiere-unsigned.apk'
    temporary=base/'build/native-refresh.apk'
    with zipfile.ZipFile(apk) as inp, zipfile.ZipFile(temporary,'w') as out:
        for entry in inp.infolist():
            if entry.filename=='classes7.dex':continue
            out.writestr(entry,inp.read(entry))
        out.write(base/'build/native-dex/classes.dex','classes7.dex')
    temporary.replace(apk)
elif mode=='inject':
    apk=base/'dist/VLC-Premiere-unsigned.apk'
    with zipfile.ZipFile(apk,'a') as z:
        assert 'classes7.dex' not in z.namelist()
        z.write(base/'build/native-dex/classes.dex','classes7.dex')
    print('Native UI DEX injected into rebuilt VLC package')
else:raise SystemExit('Use prepare or inject')
