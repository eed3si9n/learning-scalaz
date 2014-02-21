---
out: Hom-sets.html
---

## Hom 集合

飛ばした概念もあるので、少し前に戻る。

### 大きい圏、小さい圏、局所的に小さい圏

> **定義 1.11.** 任意の圏 **C** において、**C** の対象の集まり **C<sub>0</sub>** と、**C** の射の集まり **C<sub>1</sub>** が集合であるとき、小さい圏という。その他の場合は、**C** は大きいという。
>
> 例えば、全ての有限圏は明らかに小さいが、有限集合と関数の圏である **Sets<sub>fin</sub>** も小さい。

**Cat** は、実は全ての小さい圏の圏であるため、**Cat** は自身を含まない。

> **定義 1.12.** 任意の圏 **C** は、**C** の全ての対象 X と Y について、Hom<sub><b>C</b></sub>(X, Y) = { f ∈ **C<sub>1</sub>** | f: X = Y } という集まりが集合であるとき、局所的に小さいといい、これを hom集合という。 

### Hom集合

**Hom集合** (Hom-set) は Hom(*A*, *B*) と表記され、対象 *A* と *B* の射の集合だ。Hom集合は対象を射だけを使って検査 (要素を見ること) することができるため、役に立つ。

**C** の任意の射 *f*: *A* => *B* を Hom(*X*, *A*) に入れると関数が得られる:

- Hom(*X*, *f*): Hom(*X*, *A*) => Hom(*X*, *B*)
- case *x* => (*f* ∘ *x*: *X* => *A* => *B*)

つまり、Hom(*X*, *f*) = f ∘ `_` となる。

**Sets** 圏で単集合のトリックを使うことで、*A* ≅ Hom<sub><b>Sets</b></sub>(*1*, *A*) であることを利用できる。これを一般化すると、Hom(*X*, *A*) は、*X* から見た generalized element の集合だと考えることができる。<br>
![generalized elements](../files/day21-b-generalized-elements.png)

*A* を `_` で置換することで函手を作ることができる<br>
> Hom(*X*, `_`): **C** => **Sets**.<br>
![representable functor](../files/day21-c-representable-functor.png)

この函手は representable functor または共変Hom函手と呼ばれる。

### Hom集合で考える

> 任意の対象 *P* について、射のペア *p<sub>1</sub>*: P => A と *p<sub>2</sub>*: P => B によって集合<br>
> Hom(P, A) × Hom(P, B)<br>
> の要素 (p<sub>1</sub>, p<sub>2</sub>) を決定することができる。

![product of objects](../files/day20-d-product-of-objects.png)<br>

上の図式によって、*x: X => P* が与えられれば、それを *p<sub>1</sub>* と *p<sub>2</sub>* に合成することによって、それぞれ *x<sub>1</sub>* と *x<sub>2</sub>* が得られることが分かる。射の合成は Hom集合では関数であるため、これも関数として書くことができる:<br>

*ϑ<sub>X</sub>* = (Hom(*X*, *p<sub>1</sub>*), Hom(*X*, *p<sub>2</sub>*)): Hom(*X*, *P*) => Hom(*X*, *A*) × Hom(*X*, *B*)<br>
ただし *ϑ<sub>X</sub>*(*x*) = (*x<sub>1</sub>*, *x<sub>2</sub>*)



> **命題 2.20.** 以下の形式の図式<br>
> ![product diagram](../files/day20-g-product-diagram.png)<br>
> は、全ての対象 X に関して、(2.1) で与えられるカノニカル関数 *ϑ<sub>X</sub>* が同型<br>
> *ϑ<sub>X</sub>*: Hom(X, P) ≅ Hom(P, A) × Hom(P, B)<br>
> であり、その時に限り、A と B の積である。

これは図式を同型等式で入れ替えることができたという意味で興味深い。
