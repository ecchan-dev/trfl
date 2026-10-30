#!/usr/bin/env python3
"""Exercise live boards, catalog/list, thread, and bookmark persistence via adb."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get('ADB', os.path.expanduser('~/Android/Sdk/platform-tools/adb'))
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')

def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], text=True)

def nodes():
    for attempt in range(3):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/trfl-smoke.xml')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/trfl-smoke.xml')).iter('node'))
        except subprocess.CalledProcessError:
            if attempt == 2:
                raise
            time.sleep(1)

def find(label, timeout=30):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        for node in nodes():
            if label in (node.get('text'), node.get('content-desc')) and node.get('class') != 'android.widget.EditText':
                return node
        time.sleep(0.5)
    raise AssertionError(f'UI element not found: {label}')

def tap(label):
    find(label)
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/trfl-smoke.xml'))
    parents = {child: parent for parent in root.iter() for child in parent}
    for match in root.iter('node'):
        if label not in (match.get('text'), match.get('content-desc')) or match.get('class') == 'android.widget.EditText':
            continue
        node = match
        while node is not None and node.get('clickable') != 'true':
            node = parents.get(node)
        if node is not None:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            return
    raise AssertionError(f'No clickable control: {label}')

adb('shell', 'am', 'force-stop', 'org.trfl.app')
adb('shell', 'am', 'start', '-n', 'org.trfl.app/.MainActivity')
find('Trfl')
# Search allows reaching the board regardless of server board ordering.
tap('Search')
find('Find a board')
adb('shell', 'input', 'text', 'Technology')
adb('shell', 'input', 'keyevent', '111')
favorite_controls = [n for n in nodes() if n.get('checkable') == 'true' and any(c.get('content-desc') == 'Favorite /g/' for c in n.iter('node'))]
if not favorite_controls or favorite_controls[0].get('checked') != 'true':
    tap('Favorite /g/')
find('Drag /g/ to reorder')
tap('Technology')
find('/g/')
find('Switch catalog/list')
tap('Switch catalog/list')
end = time.monotonic() + 30
while True:
    candidates = [n for n in nodes() if n.get('text', '').endswith(' replies')]
    if candidates:
        n = candidates[0]
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        break
    if time.monotonic() > end:
        raise AssertionError('No live catalog threads loaded')
find('More actions')
watch_controls = [n for n in nodes() if n.get('checkable') == 'true' and any(c.get('content-desc') == 'Watch thread' for c in n.iter('node'))]
if not watch_controls or watch_controls[0].get('checked') != 'true':
    tap('Watch thread')
tap('Thread images')
# A live technology thread may have only its OP attachment.
end = time.monotonic() + 15
while time.monotonic() < end:
    attachments = [n for n in nodes() if n.get('content-desc', '').startswith('Open attachment ')]
    if attachments:
        tap(attachments[0].get('content-desc'))
        find('Close media')
        find('Next attachment')
        tap('Close media')
        break
else:
    raise AssertionError('Thread gallery did not load')
tap('Thread images')
tap('More actions')
if any(n.get('text') == 'Save thread locally' for n in nodes()):
    tap('Save thread locally')
else:
    adb('shell', 'input', 'keyevent', '4')
adb('shell', 'input', 'keyevent', '4')
find('/g/')
adb('shell', 'input', 'keyevent', '4')
find('Trfl')
tap('Saves')
find('Threads')
find('Photos')
find('Videos')
assert any(n.get('text', '').startswith('/g/') for n in nodes()), 'Saved thread missing'
row = next(n.get('text') for n in nodes() if n.get('text', '').startswith('/g/'))
entry = row.split(' · ')[0]
tap('Saved thread options ' + entry)
tap('Archive')
tap('Archived saves')
tap('Saved thread options ' + entry)
tap('Remove from Saves')
find('Remove from Saves?')
tap('Cancel')
tap('Saved thread options ' + entry)
tap('Unarchive')
tap('Watched')
assert any(n.get('text', '').startswith('/g/') for n in nodes()), 'Watched thread missing'
tap('Watched options')
find('Prune dead threads')
tap('Mark all as read')
tap('Settings')
find('4chan colors')
find('18+ boards')
find('4chan appearance')
tap('Boards')
find('Trfl')
tap('Search')
find('Find a board')
tap('Close search')
find('Trfl')
assert not any(n.get('class') == 'android.widget.EditText' for n in nodes()), 'Search did not close'
print('PASS: live browsing, favorites/reorder controls, gallery, watchlist, bookmarks, navigation, settings and app-bar search')
