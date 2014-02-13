  [product]: http://www.scala-lang.org/api/2.10.3/index.html#scala.Product

## Products

> Let us begin by considering products of sets. Given sets A and B, the cartesian product of A and B is the set of ordered pairs<br>
> A × B = {(a, b)| a ∈ A, b ∈ B}

There are two coordinate projections:<br>
![coordinate projections](files/day20-b-coordinate-projections.png)

with:

- *fst ∘ (a, b) = a*
- *snd ∘ (a, b) = b*

This notion of product relates to [scala.Product][product], which is the base trait for all tuples and case classes.

For any element in *c ∈ A × B*, we have *c = (fst ∘ c, snd ∘ c)*

Using the same trick as yesterday, we can introduce the singleton explicitly:

![product of sets](files/day20-c-product-of-sets.png)

The (external) diagram captures what we stated in the above. If we replace 1-elements by generalized elements, we get the categorical definition.

> **Definition 2.15.** In any category **C**, a product diagram for the objects A and B consists of an object P and arrows p<sub>1</sub> and p<sub>2</sub><br>
> ![product diagram](files/day20-g-product-diagram.png)<br>
> satisfying the following UMP:
> 
> Given any diagram of the form<br>
> ![product definition](files/day20-h-product-definition.png)<br>
> there exists a unique u: X => P, making the diagram<br>
> ![product of objects](files/day20-d-product-of-objects.png)<br>
> commute, that is, such that x<sub>1</sub> = p<sub>1</sub> u and x<sub>2</sub> = p<sub>2</sub> u.

Because this is universal, this applies to any category.

### Uniqueness of products

UMP also suggests that all products of *A* and *B* are unique up to isomorphism.

> **Proposition 2.17** Products are unique up to isomorphism.

Suppose we have *P* and *Q* that are products of objects *A* and *B*.

![uniqueness of products](files/day20-i-uniqueness-of-products.png)

1. Because *P* is a product, there is a unique *i: P => Q* such that *p<sub>1</sub> = q<sub>1</sub> ∘ i* and *p<sub>2</sub> = q<sub>2</sub> ∘ i*
2. Because *Q* is a product, there is a unique *j: Q => P* such that *q<sub>1</sub> = p<sub>1</sub> ∘ j* and *q<sub>2</sub> = p<sub>2</sub> ∘ j*
3. By composing *j* and *i* we get *1<sub>P</sub> = j ∘ i*
4. Similarly, we can also get *1<sub>Q</sub> = i ∘ j*
5. Thus *i* is an isomorphism, and *P ≅ Q* ∎

Since all products are isometric, we can just denote one as *A × B*, and the arrow *u: X => A × B* is denoted as *⟨x<sub>1</sub>, x<sub>2</sub>⟩*.

### Examples

We saw that in **Sets**, cartesian product is a product.

> Let *P* be a poset and consider a product of elements p, q ∈ P. We must have projections
> 
> - p × q ≤ p
> - p × q ≤ q
>
> and if for any element x, x ≤ p, and x ≤ q<br>
> then we need
>
> - x ≤ p × q

In this case, *p × q* becomes greatest lower bound.
