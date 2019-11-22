import json
import os

import urllib2
import gzip
import io
import sys

def download_docs():
    if not os.path.exists('archives'):
        os.makedirs('archives')
    with open('index.json') as json_file:
        data = json.load(json_file)
        for p in data['docsets']:
            archive = data['docsets'][p]['archive']
            url = 'http://newyork.kapeli.com/feeds/zzz/user_contributed/build/' + p + '/' + archive
            sock = urllib2.urlopen(url)
            with open('archives/' + archive, 'wb') as out_file:
                out_file.write(sock.read())
                print('Downloaded: ' + archive)
            sock.close()

def fetch_docs():
    if not os.path.exists('archives_extra'):
        os.makedirs('archives_extra')
    f = open("feeds.txt", "r")
    name = f.readline().strip()
    while name:
        archive = name + '.tgz'
        url = sys.argv[1] + archive
        sock = urllib2.urlopen(url)
        with open('archives_extra/' + archive, 'wb') as out_file:
            out_file.write(sock.read())
            print('Downloaded: ' + archive)
        sock.close()
                
        name = f.readline().strip()

if __name__ == '__main__':
    if len(sys.argv) == 1:
        download_docs()
    else:
        fetch_docs()
