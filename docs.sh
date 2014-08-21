#!/bin/sh

pf docs www

cd www
perl -0777 -pe 's/learning Scalaz\n===============/---\ntitle: learning Scalaz\nauthor: eugene yokota (\@eed3si9n)\ntags: [scala, scalaz]\n...\n\npreface\n-------/is' "Combined+Pages.md" > learning-scalaz.md
pandoc learning-scalaz.md -o learning-scalaz.pdf --latex-engine=xelatex --toc

cd ja
perl -0777 -pe 's/独習 Scalaz\n===============/---\ntitle: learning Scalaz\nauthor: eugene yokota (\@eed3si9n)\ntags: [scala, scalaz]\n...\n\n前書き\n-------/is' "Combined+Pages.md" > learning-scalaz_ja.md
pandoc learning-scalaz_ja.md -o learning-scalaz_ja.pdf -V documentclass=ltjarticle --latex-engine=lualatex --toc
cd ../
cd ../
