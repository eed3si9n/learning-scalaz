---
out: determination-and-choice.html
---

### 決定問題と選択問題

CM:

> <nsbr>1. **決定問題** (determination problem; もしくは extension)<br>
> 図に表す *f* と *g* があるとき、*h = g ∘ f* が成り立つ *g* があれば全て挙げよ。

![determination](../files/day19-h-determination.png)


> <nsbr>2. **選択問題** (choice problem; もしくは lifting)<br>
> 図に表す *g* と *h* があるとき、*h = g ∘ f* が成り立つ *g* があれば全て挙げよ。

![choice](../files/day19-i-choice.png)

これら 2つの問題は割り算に類似している。

### レトラクションとセクション

> **定義**: *f: A => B* があるとき、
>
> - *r ∘ f = 1<sub>A</sub>* が成り立つ射 *r: B => A* を**f のレトラクション** (retraction) という。
> - *f ∘ s = 1<sub>B</sub>* が成り立つ射 *s: B => A* を**f のセクション** (section) という。


レトラクション問題の外部図式はこうなる:

![retraction](../files/day19-j-retraction.png)

セクション問題の外部図式:

![section](../files/day19-k-section.png)

### 全射

> もし射 *f: A => B* に関して「任意の射 *y: T => B* に対して *f ∘ x = y* が成り立つ射 *x: T => A* が存在する」という条件が成り立つ場合、「f は T からの射に関して**全射** (surjective) である」という。

まずは集合論で全射 (surjective) と言ったときどういう意味なのかを考えるのに独自の例を作ってみた: <br>![surjective](../files/day19-l-surjective.png)<br>
John が友達とインドに行くとして、飛行機の昼食に二つの選択があるとする: チキンラップサンドかスパイシーなヒヨコ豆だ。全射ということは各メニューに対して最低一人はその選択を選んだ人がいるということだ。言い換えると、コドメイン内の要素が網羅されている。

ここで単集合を導入することで要素という考えを一般化できることを思い出してほしい。<br>![surjective with singleton](../files/day19-m-surjective-with-singleton.png)

これを圏論での全射の定義と比較してみよう: 任意の射 *y: T => B* に対して *f ∘ x = y* が成り立つ射 *x: T => A* が存在する。どの *1* から *B* (昼食) への射に対しても、*f ∘ x = y* が成立する *1* から *A* (人) への射が存在する。つまり、*f* は *1* からの射に関して全射であると言える。

これを外部図式で表してみる。<br>![surjective external diagram](../files/day19-n-surjective-external-diagram.png)<br> 選択問題と同じ形になった。

### 単射とモノ射

> **定義**: もし射 *f* に関して「任意の射のペア *x<sub>1</sub>: T => A* と *x<sub>2</sub>: T => A* に対して *f ∘ x<sub>1</sub> = f ∘ x<sub>2</sub>* ならば *x<sub>1</sub> = x<sub>2</sub>* である」という条件が成り立つ場合、「f は T からの射に関して**単射** (injective) である」という。
>
> もし *f* が全ての *T* からの射に関して単射である場合、*f* は単射である、または**モノ射** (monomorphism) であるという。

集合論で考えた場合の単射はこうなる: <br> ![injective](../files/day19-o-injective.png)

コドメイン内の要素は全て一度だけ写像されている。ここで 3つ目の対象 *T* を頭の中で想像して、そこから John、Mary、Sam への射が出ているとする。どの射を合成してもユニークな食事へと届くはずだ。外部図式だとこうなる:<br>![monomorphism](../files/day19-p-monomorphism-external-diagram.png)

### エピ射

> **定義**: もし射 *f* に関して「任意の射のペア *t<sub>1</sub>: T => A* と *t<sub>2</sub>: T => A* に対して *t<sub>1</sub> ∘ f = t<sub>2</sub> ∘ f* ならば *t<sub>1</sub> = t<sub>2</sub>* である」という条件が全ての *T* において成り立つ場合、**エピ射** (epimorphism) であるという。

これは全射の一般形らしいけども、本ではそこの所をつっこんでないので飛ばすことにする。

### 冪等射

> **定義**: 自己準同型射 (endomorphism) について *e ∘ e = e* が成り立つとき**冪等射** (idempotent) という。

冪等の読みはベキトウ。

### 自己同型射

> 自己準同型 (endomorphism) であり、かつ同型 (isomorphism) である射のことを**自己同型射** (automorphism) という。

結構進めたので、これぐらいにしておこう。圏という考えが一度内部図式にバラすことで分かりやすくなったと思う。
