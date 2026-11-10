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
    gradle = Path('android/common-android.gradle').read_text()
    version = re.search(r"versionName\s+'([^']+)'", gradle).group(1)
    if tag != 'v' + version:
        raise ValueError('Release tag must match Android versionName')
    notes = Path('release-notes') / (tag + '.md')
    body = notes.read_text(encoding='utf-8')
    apks = []
    for name in ('kotoba-lite.apk', 'kotoba-full.apk'):
        apk = Path('release-apk') / name
        with apk.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        apks.append({'name': name, 'path': apk, 'bytes': apk.stat().st_size, 'sha256': digest})
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

    # The by-tag endpoint only resolves published releases. List authenticated
    # releases too, so a draft/retried upload is resumed instead of duplicated.
    release = None
    page = 1
    while release is None:
        releases = request(base + '/releases?per_page=100&page=' + str(page))
        matches = [item for item in releases if item['tag_name'] == tag]
        if len(matches) > 1:
            raise ValueError('Multiple releases use the same tag; resolve before publishing')
        if matches:
            release = matches[0]
        if len(releases) < 100:
            break
        page += 1
    if release is None:
        release = json_request(base + '/releases', 'POST', {'tag_name': tag, 'target_commitish': sha,
                               'name': 'Kotoba Android ' + version, 'body': body, 'draft': True, 'prerelease': False})
    if release['target_commitish'] != sha:
        raise ValueError('Existing release targets a different commit')
    assets = {asset['name']: asset for asset in request(release['assets_url'])}
    upload_url = release['upload_url'].split('{')[0]
    checksum = ''.join(apk['sha256'] + '  ' + apk['name'] + '\n' for apk in apks).encode()
    files = [(apk['name'], apk['bytes'], 'application/vnd.android.package-archive', apk['path'], apk['sha256']) for apk in apks]
    files.append(('SHA256SUMS', len(checksum), 'text/plain', checksum, hashlib.sha256(checksum).hexdigest()))
    for name, size, mime, source, expected_hash in files:
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
    print(json.dumps({'release': release['html_url'], 'apks': [{key: value for key, value in apk.items() if key != 'path'} for apk in apks]}))

if __name__ == '__main__':
    main()
