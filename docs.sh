#!/bin/sh

pf docs www
cp docs/favicon.ico www/favicon.ico
pf docs.ja www.ja
cp docs.ja/favicon.ico www.ja/favicon.ico
