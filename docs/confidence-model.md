# Confidence model

Confidence describes how strongly one edge follows from static evidence. It is
not the probability that production executes the code and not a quality score.

## Levels

### HIGH

The analyzer has explicit, structurally parsed evidence with no unresolved part
needed for the claimed relation. Examples: a complete SQL literal parsed as an
`UPDATE`; a string constant whose initializer is completely resolved from
literals/final constants; explicit `@Table(name="PEDIDO")` or
`@Column(name="STATUS")`; a direct call resolved to a source declaration.

### MEDIUM

The claim follows from a documented, narrow inference. Examples: a SQL string
with unresolved value placeholders that do not alter identifiers or grammar;
an annotation default derived from a Java type/field name; a uniquely matched
same-project method by owner/name/arity without full type resolution.

### LOW

There is a relevant textual or structural signal, but insufficient resolution
to assert a dependency. LOW evidence may identify a review location but cannot
create a table/column read/write edge unless the target identifier itself is
known. Examples: a SQL-looking string passed through an unknown helper or an
ambiguous unqualified column in a multi-table query.

### UNKNOWN

The analyzer detected a candidate but cannot determine the claimed target or
operation. Example: `"SELECT * FROM " + tableName`. UNKNOWN is represented as a
diagnostic/finding tied to the source location, not as an edge to an invented
database node.

## String-expression rules

| Expression | Result |
| --- | --- |
| Complete literal or text block | resolved, HIGH |
| Concatenation of resolved literals/constants | resolved, HIGH |
| Resolved SQL structure plus runtime value used only as a value | resolved, MEDIUM |
| Runtime fragment in an identifier/keyword position | unresolved, UNKNOWN |
| Cyclic/ambiguous constant reference | unresolved, UNKNOWN |

Java 8 targets do not contain text blocks, but the analyzer runtime may accept
newer source roots when configured.

## Propagation

Graph edges retain their own level. Reports never overwrite raw confidence.
For a displayed path, confidence is the minimum level under the ordering
`HIGH > MEDIUM > LOW > UNKNOWN`; `POSSIBLY_CALLS` imposes a maximum of MEDIUM.
The output always shows the contributing evidence so the user can disagree with
the inference.

## Prohibited upgrades

- Parser success alone does not upgrade an ambiguous column owner.
- Repeated textual matches do not become HIGH by quantity.
- A framework naming convention does not become explicit mapping evidence.
- A call through an interface, proxy, reflection, or DI container is not
  `CALLS` without a resolved source target.
