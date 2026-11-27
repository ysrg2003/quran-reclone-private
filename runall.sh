#!/bin/bash
cd /downloads/quran-study/asr; source ../wvenv/bin/activate
while true; do
  python run.py Alafasy_128kbps small keys_all.txt >> /tmp/asrall.log 2>&1
  done_n=$(cut -f1 tr_Alafasy_128kbps_small.tsv | sort -u | wc -l)
  [ "$done_n" -ge 6229 ] && break
  sleep 5
done
echo ALLDONE >> /tmp/asrall.log
