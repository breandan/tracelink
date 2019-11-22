import json
import os

import urllib.request
import gzip
import io

def download_docs():
    if not os.path.exists('archives'):
        os.makedirs('archives')
    with open('index.json') as json_file:
        data = json.load(json_file)
        for p in data['docsets']:
            archive = data['docsets'][p]['archive']
            url = 'http://newyork.kapeli.com/feeds/zzz/user_contributed/build/' + p + '/' + archive
            with urllib.request.urlopen(url) as response, open('archives/' + archive, 'wb') as out_file:
                out_file.write(response.read())
                print('Downloaded: ' + archive)

if __name__ == '__main__':
    download_docs()