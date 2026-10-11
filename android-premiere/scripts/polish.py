#!/usr/bin/env python3
"""Extend Premiere's native component system without changing view/binding graphs."""
from pathlib import Path
import json, re, sys
import xml.etree.ElementTree as ET

work = Path(sys.argv[1])
res = work / 'res'
A = '{http://schemas.android.com/apk/res/android}'
P = '{http://schemas.android.com/apk/res-auto}'
ET.register_namespace('android', A[1:-1])
ET.register_namespace('app', P[1:-1])
changed = []

styles = res / 'values/styles.xml'
s = styles.read_text()
def style(name, items):
    global s
    start = s.index(f'<style name="{name}"')
    end = s.index('</style>', start) + 8
    b = s[start:end]
    for key, value in items.items():
        pat = rf'(<item name="{re.escape(key)}">).*?(</item>)'
        if re.search(pat, b):
            b = re.sub(pat, lambda m: m[1] + value + m[2], b)
        else:
            b = b.replace('</style>', f'    <item name="{key}">{value}</item>\n    </style>')
    s = s[:start] + b + s[end:]

for name, dark in [('Theme.VLC.Apearance', False), ('Theme.VLC.Apearance.Black', True)]:
    text = '@color/premiere_ivory' if dark else '@color/premiere_light_ink'
    muted = '@color/premiere_muted' if dark else '@color/premiere_light_muted'
    panel = '@color/premiere_panel' if dark else '@color/premiere_light_panel'
    background = '@color/premiere_ink' if dark else '@color/premiere_light_bg'
    style(name, {
        'android:colorBackground':background,
        'android:fontFamily':'sans-serif', 'fontFamily':'sans-serif',
        'android:textColorPrimary':text, 'android:textColorSecondary':muted,
        'colorOnSurface':text, 'colorSurface':panel, 'colorControlNormal':muted,
        'colorOnPrimary':'@color/premiere_ink', 'colorOnSecondary':'@color/premiere_ink',
        'about_text_primary':text, 'ariane_text_color':muted,
        'permission_explanation_background':panel, 'dialog_circle_background':panel,
        'bottom_sheet_radius':'24.0dp', 'mini_visualizer_color':'@color/premiere_orange',
        'audio_chips_color':panel, 'audio_chips_text_color':text,
    })
style('Theme.VLC.Player.7', {'bottom_sheet_radius':'24.0dp','colorSurface':'@color/premiere_panel',
    'android:colorBackground':'@color/premiere_ink',
    'colorOnSurface':'@color/premiere_ivory','android:textColorPrimary':'@color/premiere_ivory',
    'android:textColorSecondary':'@color/premiere_muted','mini_visualizer_color':'@color/premiere_orange'})
style('Theme.VLC.Onboarding', {'android:statusBarColor':'@color/premiere_ink','android:navigationBarColor':'@color/premiere_ink'})
style('Theme.VLC.Onboarding.7', {'background_default':'@color/premiere_ink','background_actionbar':'@color/premiere_ink','colorControlNormal':'@color/premiere_ivory'})
style('ToolbarTitleText', {'android:textColor':'?font_default','android:textSize':'28.0sp','android:fontFamily':'sans-serif-medium','android:letterSpacing':'-0.025'})
style('Toolbar.VLC', {'colorControlNormal':'?font_default'})
style('Theme.VLC.BottomSheetTitle', {'android:textSize':'22.0sp','android:fontFamily':'sans-serif-medium','android:minHeight':'64.0dp'})
style('VLC.TextViewTitle', {'android:textSize':'16.0sp','android:fontFamily':'sans-serif-medium'})
style('VLC.TextViewDescription', {'android:textSize':'13.0sp','android:ellipsize':'end'})
style('VLC.Onboarding.Title', {'android:textSize':'28.0sp','android:textColor':'@color/premiere_ivory','android:letterSpacing':'-0.02'})
style('VLC.Onboarding.Text', {'android:textColor':'@color/premiere_muted','android:lineSpacingExtra':'4.0dp'})
style('VLCPreferenceTheme.Dark', {'android:textColorPrimary':'@color/premiere_ivory','android:textColorSecondary':'@color/premiere_muted'})
style('VLCPreferenceTheme.Light', {'android:textColorPrimary':'@color/premiere_light_ink','android:textColorSecondary':'@color/premiere_light_muted'})
style('TabLayout.VLC', {'tabIndicatorHeight':'2.0dp','tabSelectedTextColor':'?font_default','tabTextColor':'?font_light','tabIndicatorColor':'@color/premiere_orange'})
style('Widget.App.CardView', {'cardCornerRadius':'16.0dp','cardBackgroundColor':'?card_background','strokeColor':'?card_border'})
s = s.replace('</resources>', '''    <style name="Premiere.NavigationLabel" parent="@style/TextAppearance.MaterialComponents.Caption">
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textSize">11.0sp</item>
    </style>
</resources>''')
styles.write_text(s)

def shape(name, color, radius=16, stroke=None):
    text = f'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="{color}"/><corners android:radius="{radius}.0dp"/>'
    if stroke: text += f'<stroke android:width="1.0dp" android:color="{stroke}"/>'
    (res/'drawable'/f'{name}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n'+text+'</shape>\n')
shape('premiere_library_card', '?card_background', 16, '?card_border')
shape('premiere_library_card_selected', '@color/premiere_orange_soft', 16, '@color/premiere_orange')
(res/'drawable/premiere_transport.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="#33FFFFFF"><item><shape android:shape="oval"><solid android:color="@color/premiere_orange"/></shape></item></ripple>
''')
for name in ['video_grid_card_background','video_grid_card_background_dark']:
    f = res/'drawable'/f'{name}.xml'
    t = f.read_text().replace('android:drawable="@color/transparent"','android:drawable="@drawable/premiere_library_card"')
    f.write_text(t)
for name in ['video_grid_card_background_selected_dark','video_grid_card_background_focused_dark', 'video_grid_card_background_selected','video_grid_card_background_focused']:
    f = res/'drawable'/f'{name}.xml'
    if f.exists(): f.write_text('''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android"><item android:drawable="@drawable/premiere_library_card_selected"/></selector>
''')
for name in ['advanced_options_background_dark','advanced_options_background_light','advanced_options_background']:
    f = res/'drawable'/f'{name}.xml'
    t = f.read_text()
    t = re.sub(r'<solid android:color="[^"]+"', '<solid android:color="?bottom_sheet_background"', t)
    t = t.replace('4.0dp','20.0dp')
    f.write_text(t)

def edit(name, fn, folder='layout'):
    f = res/folder/f'{name}.xml'
    if not f.exists(): raise SystemExit(f'Missing phone layout: {name}')
    r = ET.parse(f).getroot()
    fn(r)
    ET.indent(r, space='    ')
    ET.ElementTree(r).write(f, encoding='utf-8', xml_declaration=True)
    changed.append(folder+'/'+name+'.xml')
def node(r, ident):
    matches = [e for e in r.iter() if e.get(A+'id') == '@id/'+ident]
    assert len(matches) == 1, ident
    return matches[0]
def attrs(e, **values):
    for k, v in values.items():
        e.set((P if k.startswith('app_') else A) + k.removeprefix('app_'), v)

def toolbar(r):
    attrs(node(r,'main_toolbar'), layout_height='72.0dp', app_titleTextColor='?font_default', app_titleMarginStart='20.0dp')
edit('toolbar', toolbar)
def navigation(r):
    attrs(node(r,'navigation'), app_elevation='0.0dp', app_itemIconSize='22.0dp', app_itemTextAppearanceActive='@style/Premiere.NavigationLabel', app_itemTextAppearanceInactive='@style/Premiere.NavigationLabel')
edit('main', navigation)

def card(r):
    attrs(r, layout_width='136.0dp')
    for e in r.iter():
        if e.tag.endswith('MaterialCardView'):
            attrs(e, app_cardCornerRadius='14.0dp', app_cardElevation='0.0dp')
            if e.get(A+'id') == '@id/container': attrs(e, app_layout_constraintDimensionRatio='2:3')
    for ident in ['title','ml_item_title']:
        for e in r.iter():
            if e.get(A+'id') == '@id/'+ident: attrs(e, fontFamily='sans-serif-medium')
edit('video_grid_card', card)
edit('audio_browser_card_item', card)
def browser(r):
    attrs(r, minHeight='72.0dp', paddingTop='4.0dp', paddingBottom='4.0dp')
    attrs(node(r,'title'), textSize='16.0sp', fontFamily='sans-serif-medium')
    attrs(node(r,'text'), textSize='13.0sp')
edit('browser_item', browser)
def playlist(r):
    attrs(node(r,'audio_item_title'), fontFamily='sans-serif-medium')
    attrs(node(r,'audio_item_subtitle'), textSize='13.0sp')
    attrs(node(r,'coverImage'), layout_height='56.0dp', scaleType='centerCrop')
edit('playlist_item', playlist)
def album(r):
    attrs(r, minHeight='72.0dp', paddingTop='4.0dp', paddingBottom='4.0dp')
    attrs(node(r,'subtitle'), textSize='13.0sp')
edit('audio_album_track_item', album)
def option(r):
    attrs(r, minHeight='64.0dp', paddingStart='20.0dp', paddingEnd='20.0dp')
    attrs(node(r,'option_title'), textColor='?font_default', textSize='16.0sp')
edit('player_option_item', option)

def audio_player(r):
    attrs(node(r,'header_play_pause'), layout_width='48.0dp', layout_height='48.0dp', padding='8.0dp', background='@drawable/premiere_transport')
    attrs(node(r,'header_large_play_pause'), layout_width='64.0dp', layout_height='64.0dp', padding='10.0dp', background='@drawable/premiere_transport')
    attrs(node(r,'play_pause'), layout_width='72.0dp', layout_height='72.0dp', padding='12.0dp', background='@drawable/premiere_transport')
edit('audio_player', audio_player)
edit('audio_player', audio_player, 'layout-land')
def hud(r):
    attrs(node(r,'player_overlay_play'), layout_width='64.0dp', layout_height='64.0dp', padding='12.0dp', background='@drawable/premiere_transport')
    for ident in ['player_overlay_time','player_overlay_length']:
        attrs(node(r, ident), textColor='@color/premiere_ivory', textSize='13.0sp', fontFamily='sans-serif-medium')
edit('player_hud', hud)
def about(r):
    attrs(node(r,'textView7'), textSize='28.0sp', fontFamily='sans-serif-medium')
    for e in r.iter():
        if e.tag.endswith('CardView'): attrs(e, app_cardCornerRadius='16.0dp', app_cardElevation='0.0dp')
edit('about', about)
def equalizer(r):
    attrs(node(r,'cardView'), app_cardCornerRadius='16.0dp', app_cardBackgroundColor='?card_background', app_strokeColor='?card_border')
    attrs(node(r,'preset_title'), fontFamily='sans-serif-medium')
edit('dialog_equalizer', equalizer)
def onboarding(r):
    attrs(node(r,'next_button'), minHeight='52.0dp', layout_marginEnd='24.0dp', layout_marginBottom='24.0dp', app_cornerRadius='16.0dp')
    attrs(node(r,'skip_button'), minHeight='52.0dp', layout_marginBottom='24.0dp')
edit('activity_onboarding', onboarding)

# Consistent body text and primary actions for native sheets, including remote setup.
# Only existing controls receive attributes; no new binding nodes are introduced.
sheet_names = ['dialog_playback_speed','dialog_playlist','dialog_rename','dialog_select_chapter',
    'dialog_time_picker','dialog_display_settings','dialog_video_resume','dialog_confirm_delete',
    'dialog_renderers','dialog_audio_controls_settings','dialog_video_controls_settings',
    'player_overlay_tracks','player_overlay_track_item','player_overlay_track_option_item',
    'player_overlay_settings','remote_access_onboarding_welcome','remote_access_onboarding_how',
    'remote_access_onboarding_otp','remote_access_onboarding_ssl']
def sheet(r):
    for e in r.iter():
        if e.tag == 'TextView' and e.get(A+'textSize') == '12.0sp': attrs(e, textSize='13.0sp')
        if e.tag.endswith('MaterialButton'):
            attrs(e, app_cornerRadius='14.0dp', minHeight='48.0dp')
        if e.get(A+'textColor') in ['@color/orange500','@color/orange600','@color/orange800']:
            attrs(e, textColor='@color/premiere_orange')
        if e.get(A+'background') == '@color/white_transparent_10': attrs(e, background='?default_divider')
for name in sheet_names: edit(name, sheet)

# Update the default selected theme, preserving later explicit choices (Light/System).
f = work/'smali_classes6/org/videolan/vlc/gui/onboarding/OnboardingViewModel.smali'
t = f.read_text()
old = ':goto_0\n    iput v0, p0, Lorg/videolan/vlc/gui/onboarding/OnboardingViewModel;->theme:I'
assert old in t
f.write_text(t.replace(old, ':goto_0\n    const/4 v0, 0x2\n\n    iput v0, p0, Lorg/videolan/vlc/gui/onboarding/OnboardingViewModel;->theme:I', 1))
f = work/'smali_classes6/org/videolan/vlc/gui/helpers/UiToolsKt.smali'
t = f.read_text()
old = 'const-string v3, "app_theme"\n\n    const-string v4, "-1"'
assert old in t
f.write_text(t.replace(old, 'const-string v3, "app_theme"\n\n    const-string v4, "2"', 1))

# Do not patch OnboardingThemeFragment.onViewCreated with scratch Smali
# registers: its register allocation reuses v0/p1 for non-View values, and
# injecting an onClick call at return causes Android 16 ART VerifyError after
# the notification permission grant. The default theme is set above in
# OnboardingViewModel; the original fragment handles the actual selection.

(work.parent/'build/polish-layouts.json').write_text(json.dumps(changed, indent=2)+'\n')
print(f'Polished {len(changed)} native phone layouts; binding view graphs retained')
