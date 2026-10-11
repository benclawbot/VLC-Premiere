#!/usr/bin/env python3
"""Apply the Premiere theme and native layout treatment to a prepared project."""
from pathlib import Path
import sys
work=Path(sys.argv[1])
# VLC's automatic MediaCodec choice can stall on the observed MediaTek decoder.
# Respect any stored user choice and every other vendor; only use software
# decoding when VLC is in automatic mode on MediaTek hardware.
vlc_options = work/'smali_classes5/org/videolan/resources/VLCOptions.smali'
options_text = vlc_options.read_text()
# The checksum-pinned upstream APK stores acceleration mode in v0 here;
# p0 is the VLCOptions instance, not the acceleration mode. Never alter p0.
accel_anchor = '    :goto_3\n    if-eqz v0, :cond_5'
if ':premiere_acceleration_ready' not in options_text:
    import re
    method = '.method public final setMediaOptions(Lorg/videolan/libvlc/interfaces/IMedia;Landroid/content/Context;IZ)V'
    method_start = options_text.index(method)
    method_end = options_text.index('.end method', method_start)
    method_text = options_text[method_start:method_end]
    locals_match = re.search(r'(?m)^    \.locals (\d+)$', method_text)
    if not locals_match or method_text.count(accel_anchor) != 1:
        raise SystemExit('Pinned VLC 3.7.1 acceleration structure changed; refusing unsafe hook')
    if 'const-string v0, "hardware_acceleration"' not in method_text:
        raise SystemExit('Expected hardware acceleration preference is absent')
    locals_count = int(locals_match.group(1))
    if locals_count + 1 > 15:
        raise SystemExit('Acceleration hook needs short-form unused registers')
    temporary = f'v{locals_count}'
    prefix = f'v{locals_count + 1}'
    inserted = method_text[:locals_match.start(1)] + str(locals_count + 2) + method_text[locals_match.end(1):]
    hook = f'''    :goto_3
    const/4 {temporary}, -0x1

    if-ne v0, {temporary}, :premiere_acceleration_ready

    sget-object {temporary}, Landroid/os/Build;->HARDWARE:Ljava/lang/String;

    const-string {prefix}, "mt"

    invoke-virtual {{{temporary}, {prefix}}}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result {temporary}

    if-eqz {temporary}, :premiere_acceleration_ready

    const/4 v0, 0x0

    :premiere_acceleration_ready
    if-eqz v0, :cond_5'''
    inserted = inserted.replace(accel_anchor, hook, 1)
    options_text = options_text[:method_start] + inserted + options_text[method_end:]
    vlc_options.write_text(options_text)
res=work/'res'
# A null activity icon is accepted by Apktool/AAPT2 but makes the merged package
# metadata invalid for some Android package-manager paths. Keep the activity's
# launcher entry explicit and valid in the standalone APK.
manifest=work/'AndroidManifest.xml'
manifest_text=manifest.read_text()
bad_icon='android:icon="@null" android:label="@string/app_name" android:launchMode="singleTask" android:name="org.videolan.vlc.gui.MainActivity"'
good_icon='android:icon="@mipmap/ic_launcher" android:label="@string/app_name" android:launchMode="singleTask" android:name="org.videolan.vlc.gui.MainActivity"'
if bad_icon in manifest_text:
    manifest.write_text(manifest_text.replace(bad_icon,good_icon,1))
elif good_icon not in manifest_text:
    raise SystemExit('Expected MainActivity icon metadata not found')
# Namespaced XML edits retain all view IDs, exact data-binding tags, and widget classes.
A='{http://schemas.android.com/apk/res/android}'
PALETTE='''    <color name="premiere_ink">#171615</color>
    <color name="premiere_panel">#211E1B</color>
    <color name="premiere_panel_lift">#292521</color>
    <color name="premiere_ivory">#E7DDCC</color>
    <color name="premiere_muted">#AAA196</color>
    <color name="premiere_orange">#F46B32</color>
    <color name="premiere_orange_soft">#40F46B32</color>
    <color name="premiere_divider">#3A342F</color>
    <color name="premiere_light_bg">#F4EFE7</color>
    <color name="premiere_light_panel">#FFFDF9</color>
    <color name="premiere_light_ink">#29241F</color>
    <color name="premiere_light_muted">#766D64</color>
    <color name="premiere_light_divider">#E3DACE</color>\n'''
colors=res/'values/colors.xml'
s=colors.read_text()
if 'name="premiere_ink"' not in s: s=s.replace('<resources>', '<resources>\n'+PALETTE,1)
colors.write_text(s)
styles=res/'values/styles.xml'; s=styles.read_text()
# Directly override the app-level design tokens in the existing themes. Avoid global framework palette changes.
def theme_patch(src, style_name, replacements):
    start=src.index(f'<style name="{style_name}"')
    end=src.index('</style>',start)+8
    block=src[start:end]
    for key,val in replacements.items():
        import re
        pat=rf'(<item name="{re.escape(key)}">).*?(</item>)'
        if re.search(pat,block): block=re.sub(pat,rf'\g<1>{val}\g<2>',block)
        else: block=block.replace('</style>',f'        <item name="{key}">{val}</item>\n    </style>')
    return src[:start]+block+src[end:]
light={
 'android:windowBackground':'@color/premiere_light_bg','background_default':'@color/premiere_light_bg','background_default_darker':'@color/premiere_light_divider',
 'background_actionbar':'@color/premiere_light_bg','header_background':'@color/premiere_light_bg','audio_header_background':'@color/premiere_light_panel',
 'audio_header_divider':'@color/premiere_light_divider','audio_browser_separator':'@color/premiere_orange','audio_list_background':'@drawable/audio_list_background',
 'bottom_sheet_background':'@color/premiere_light_panel','card_background':'@color/premiere_light_panel','card_border':'@color/premiere_light_divider',
 'colorAccent':'@color/premiere_orange','colorControlHighlight':'@color/premiere_orange','colorPrimary':'@color/premiere_orange','colorPrimaryDark':'@color/premiere_orange',
 'colorSecondary':'@color/premiere_orange','default_divider':'@color/premiere_light_divider','default_separator':'@color/premiere_light_divider',
 'font_default':'@color/premiere_light_ink','font_light':'@color/premiere_light_muted','font_audio_light':'@color/premiere_light_muted',
 'empty_background':'@color/premiere_light_bg','empty_foreground':'@color/premiere_light_divider','empty_title':'@color/premiere_light_ink',
 'list_title':'@color/premiere_light_ink','list_subtitle':'@color/premiere_light_muted','player_icon_color':'@color/premiere_light_ink',
 'bottom_navigation_color':'@color/premiere_light_muted','bottom_navigation_focus':'@color/orange_800_transparent_10',
 'video_card_background':'@drawable/video_grid_card_background','video_list_background':'@drawable/video_list_background',
}
dark={
 'android:windowBackground':'@color/premiere_ink','background_default':'@color/premiere_ink','background_default_darker':'@color/premiere_panel',
 'background_actionbar':'@color/premiere_ink','header_background':'@color/premiere_ink','audio_header_background':'@color/premiere_panel',
 'audio_header_divider':'@color/premiere_divider','audio_browser_separator':'@color/premiere_orange','bottom_sheet_background':'@color/premiere_panel',
 'card_background':'@color/premiere_panel','card_border':'@color/premiere_divider','colorAccent':'@color/premiere_orange',
 'colorControlHighlight':'@color/premiere_orange','colorPrimary':'@color/premiere_orange','colorSecondary':'@color/premiere_orange',
 'default_divider':'@color/premiere_divider','default_separator':'@color/premiere_divider','font_default':'@color/premiere_ivory',
 'font_light':'@color/premiere_muted','font_audio_light':'@color/premiere_muted','empty_background':'@color/premiere_ink',
 'empty_foreground':'@color/premiere_divider','empty_title':'@color/premiere_ivory','list_title':'@color/premiere_ivory',
 'list_subtitle':'@color/premiere_muted','player_icon_color':'@color/premiere_ivory','bottom_navigation_color':'@color/premiere_muted',
 'video_card_background':'@drawable/video_grid_card_background_dark','video_list_background':'@drawable/video_list_background_dark',
}
s=theme_patch(s,'Theme.VLC.Apearance',light)
s=theme_patch(s,'Theme.VLC.Apearance.Black',dark)
player={'android:colorControlHighlight':'@color/premiere_orange','background_default':'@color/premiere_ink','background_default_darker':'@color/premiere_panel','bottom_sheet_background':'@color/premiere_panel','card_border':'@color/premiere_divider','colorAccent':'@color/premiere_orange','colorControlHighlight':'@color/premiere_orange','colorPrimary':'@color/premiere_orange','default_divider':'@color/premiere_divider','font_audio_light':'@color/premiere_muted','font_default':'@color/premiere_ivory','font_light':'@color/premiere_muted','list_subtitle':'@color/premiere_muted','list_title':'@color/premiere_ivory','player_icon_color':'@color/premiere_ivory'}
s=theme_patch(s,'Theme.VLC.Player.7',player)
styles.write_text(s)
# Keep selected states and player progress on the Premiere orange.
for name in ('video_grid_card_background_selected_dark.xml','video_list_background_selected_dark.xml'):
    f=res/'drawable'/name; d=f.read_text().replace('@color/white_transparent_10','@color/premiere_orange_soft'); f.write_text(d)
f=res/'drawable/gridview_progressbar_w.xml'; d=f.read_text().replace('@color/orange500','@color/premiere_orange'); f.write_text(d)
f=res/'drawable/bottom_navigation_background_dark.xml'; d=f.read_text().replace('@color/grey800','@color/premiere_ink').replace('@color/grey875','@color/premiere_panel'); f.write_text(d)
# New installs start with Premiere dark; an explicit saved user preference remains authoritative.
pref=res/'xml/preferences_ui.xml'; s=pref.read_text().replace('android:key="app_theme" android:summary="%s" android:defaultValue="-1"','android:key="app_theme" android:summary="%s" android:defaultValue="2"'); pref.write_text(s)
# Enrich the thumbnail/card rhythm while keeping original XML formatting and every binding node intact.
def replace_once(path, old, new):
    data=path.read_text()
    if old not in data: raise SystemExit(f'Expected layout token absent in {path}: {old}')
    path.write_text(data.replace(old,new,1))
card_style=styles.read_text()
card_style=card_style.replace('<item name="cardCornerRadius">4.0dp</item>','<item name="cardCornerRadius">10.0dp</item>',1)
styles.write_text(card_style)
f=res/'layout/video_grid_card.xml'
for old,new in [('android:paddingTop="8.0dp"','android:paddingTop="12.0dp"'),('android:paddingStart="8.0dp"','android:paddingStart="10.0dp"'),('android:paddingEnd="8.0dp"','android:paddingEnd="10.0dp"'),('vlc:layout_constraintDimensionRatio="16:10"','vlc:layout_constraintDimensionRatio="16:9"'),('android:textSize="16.0sp" android:ellipsize="end" android:id="@id/ml_item_title"','android:textSize="15.0sp" android:ellipsize="end" android:id="@id/ml_item_title"'),('android:layout_marginTop="4.0dp" android:maxLines="1"','android:layout_marginTop="8.0dp" android:maxLines="1"'),('android:layout_marginBottom="8.0dp" vlc:layout_constraintBottom_toBottomOf="parent"','android:layout_marginBottom="12.0dp" vlc:layout_constraintBottom_toBottomOf="parent"')]: replace_once(f,old,new)
f=res/'layout/video_list_card.xml'
for old,new in [('android:paddingStart="8.0dp"','android:paddingStart="12.0dp" android:paddingEnd="12.0dp"'),('android:layout_width="80.0dp" android:layout_height="50.0dp"','android:layout_width="88.0dp" android:layout_height="56.0dp"'),('android:textSize="16.0sp" android:ellipsize="end" android:gravity="center_vertical" android:id="@id/ml_item_title"','android:textSize="15.0sp" android:ellipsize="end" android:gravity="center_vertical" android:id="@id/ml_item_title"')]: replace_once(f,old,new)
f=res/'layout/audio_browser_item.xml'
replace_once(f,'android:layout_width="match_parent" android:layout_height="wrap_content" android:foreground="?selectableItemBackground"','android:layout_width="match_parent" android:layout_height="wrap_content" android:paddingStart="8.0dp" android:paddingEnd="8.0dp" android:foreground="?selectableItemBackground"')
print('Applied Premiere theme tokens and native card/list treatments')
