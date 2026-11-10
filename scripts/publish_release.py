"""Publish the tested CI APK; never overwrite a different published binary."""
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def main():
    tag = os.environ['RELEASE_TAG']
    if not re.fullmatch(r'v\d+\.\d+\.\d+', tag):
        raise ValueError('Release tag must look like v1.1.0')
    gradle = Path('android/app/build.gradle').read_text()
    version = re.search(r"versionName\s+'([^']+)'", gradle).group(1)
    if tag != 'v' + version:
        raise ValueError('Release tag must match Android versionName')
    apk = Path('release-apk/kotoba-android-local.apk')
    notes = Path('release-notes') / (tag + '.md')
    body = notes.read_text(encoding='utf-8')
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    repository = os.environ['GITHUB_REPOSITORY']
    sha = os.environ['GITHUB_SHA']
    base = 'https://api.github.com/repos/' + repository
    headers = {'Authorization': 'Bearer ' + os.environ['GITHUB_TOKEN'], 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Kotoba-Release'}
    opener = urllib.request.build_opener(NoRedirect())

    def request(url, method='GET', data=None, extra=None):
        if urllib.parse.urlparse(url).hostname not in ('api.github.com', 'uploads.github.com'):
            raise ValueError('Unexpected GitHub API host')
        with opener.open(urllib.request.Request(url, data=data, method=method, headers={**headers, **(extra or {})}), timeout=300) as response:
            return json.load(response)

    def json_request(url, method, value):
        return request(url, method, json.dumps(value).encode(), {'Content-Type': 'application/json'})

    try:
        release = request(base + '/releases/tags/' + tag)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        release = json_request(base + '/releases', 'POST', {'tag_name': tag, 'target_commitish': sha,
                               'name': 'Kotoba Android ' + version, 'body': body, 'draft': True, 'prerelease': False})
    if release['target_commitish'] != sha:
        raise ValueError('Existing release targets a different commit')
    assets = {asset['name']: asset for asset in request(release['assets_url'])}
    upload_url = release['upload_url'].split('{')[0]
    checksum = (digest + '  kotoba-android.apk\n').encode()
    for name, size, mime, source, expected_hash in (
        ('kotoba-android.apk', apk.stat().st_size, 'application/vnd.android.package-archive', apk, digest),
        ('kotoba-android.apk.sha256', len(checksum), 'text/plain', checksum, hashlib.sha256(checksum).hexdigest()),
    ):
        existing = assets.get(name)
        if existing:
            if existing['state'] != 'uploaded' or existing['size'] != size or existing.get('digest') != 'sha256:' + expected_hash:
                raise ValueError('Existing asset differs; use a new release version: ' + name)
            continue
        if not release['draft']:
            raise ValueError('Refusing to add assets to an already published release')
        extra = {'Content-Type': mime, 'Content-Length': str(size)}
        url = upload_url + '?' + urllib.parse.urlencode({'name': name})
        if isinstance(source, Path):
            with source.open('rb') as stream:
                asset = request(url, 'POST', stream, extra)
        else:
            asset = request(url, 'POST', source, extra)
        if asset['state'] != 'uploaded' or asset['size'] != size or asset.get('digest') != 'sha256:' + expected_hash:
            raise ValueError('Uploaded asset verification failed')
    if release['draft']:
        release = json_request(release['url'], 'PATCH', {'draft': False, 'make_latest': 'true', 'body': body})
    print(json.dumps({'release': release['html_url'], 'apkBytes': apk.stat().st_size, 'sha256': digest}))

if __name__ == '__main__':
    main()
