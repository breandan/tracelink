import json
import os

import requests
import gzip
import io

def download_docs():
    os.makedirs(os.path.dirname('archives'), exist_ok=True)
    with open('index.json') as json_file:
        data = json.load(json_file)
        for p in data['docsets']:
            archive = data['docsets'][p]['archive']
            url = 'http://newyork.kapeli.com/feeds/zzz/user_contributed/build/' + p + '/' + archive
            response = requests.get(url, timeout=50)
            if response.status_code == 200:
                with open('archives/' + archive, 'wb') as f:
                    f.write(response.content)
                    print('Downloaded: ' + archive)

if __name__ == '__main__':
    download_docs()