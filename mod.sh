#!/bin/sh

for i in `find . -name '*.md'`
do 
  perl -pi -e 's/\$/\\\$/g;' $i
done
