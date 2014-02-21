---
out: Hom-sets.html
---

## Hom-sets

We need to pick up some of the fundamentals that I skipped over.

### Large, small, and locally small

> **Definition 1.11.** A category **C** is called small if both the collection **C<sub>0</sub>** of objects of **C** and the collection **C<sub>1</sub>** of arrows of **C** are sets. Otherwise, **C** is called large.
>
> For example, all finite categories are clearly small, as is the category **Sets<sub>fin</sub>** of finite sets and functions.

**Cat** is actually a category of all small categories, so **Cat** doesn't contain itself.

> **Definition 1.12.** A category **C** is called locally small if for all objects X, Y in **C**, the collection Hom<sub><b>C</b></sub>(X, Y) = { f ∈ **C<sub>1</sub>** | f: X = Y } is a set (called a hom-set)

### Hom-sets

A *Hom-set* Hom(*A*, *B*) is a set of arrows between objects *A* and *B*. Hom-sets are useful because we can use it to inspect (look into the elements) an object using just arrows.

Putting any arrow *f*: *A* => *B* in **C** into Hom(*X*, *A*) would create a function:<br>

- Hom(*X*, *f*): Hom(*X*, *A*) => Hom(*X*, *B*)
- case *x* => (*f* ∘ *x*: *X* => *A* => *B*)

Thus, Hom(*X*, *f*) = f ∘ `_`.

By using the singleton trick in **Sets**, we can exploit *A* ≅ Hom<sub><b>Sets</b></sub>(*1*, *A*). If we generalize this we can think of Hom(*X*, *A*) as a set of *generalized elements* from *X*.<br>
![generalized elements](files/day21-b-generalized-elements.png)

We can then create a functor out of this by replacing *A* with `_`<br> Hom(*X*, `_`): **C** => **Sets**.<br>
![representable functor](files/day21-c-representable-functor.png)

This functor is called the representable functor, or covariant hom-functor.

### Thinking in Hom-set

> For any object *P*, a pair of arrows *p<sub>1</sub>*: P => A and *p<sub>2</sub>*: P => B determine an element (p<sub>1</sub>, p<sub>2</sub>) of the set<br>
> Hom(P, A) × Hom(P, B).

![product of objects](files/day20-d-product-of-objects.png)<br>

We see that given *x: X => P* we can derive *x<sub>1</sub>* and *x<sub>2</sub>* by composing with *p<sub>1</sub>* and *p<sub>2</sub>* respectively. Because compositions are functions in Hom sets, we could express the above as a function too:<br>

*ϑ<sub>X</sub>* = (Hom(*X*, *p<sub>1</sub>*), Hom(*X*, *p<sub>2</sub>*)): Hom(*X*, *P*) => Hom(*X*, *A*) × Hom(*X*, *B*)<br>
where *ϑ<sub>X</sub>*(*x*) = (*x<sub>1</sub>*, *x<sub>2</sub>*)

That's a cursive theta, by the way.

> **Proposition 2.20.** A diagram of the form<br>
> ![product diagram](files/day20-g-product-diagram.png)<br>
> is a product for A and B iff for every object X, the canonical function *ϑ<sub>X</sub>* given in (2.1) is an isomorphism,<br>
> *ϑ<sub>X</sub>*: Hom(X, P) ≅ Hom(P, A) × Hom(P, B).

This is pretty interesting because we just replaced a diagram with an isomorphic equation. 
