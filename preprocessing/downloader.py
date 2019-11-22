import json
import os

import urllib2
import gzip
import io

def download_docs():
    if not os.path.exists('archives1'):
        os.makedirs('archives1')
    with open('index.json') as json_file:
        data = json.load(json_file)
        for p in data['docsets']:
            archive = data['docsets'][p]['archive']
            url = 'http://newyork.kapeli.com/feeds/zzz/user_contributed/build/' + p + '/' + archive
            sock = urllib2.urlopen(url)
            with open('archives1/' + archive, 'wb') as out_file:
                out_file.write(sock.read())
                print('Downloaded: ' + archive)
            sock.close()

if __name__ == '__main__':
    download_docs()