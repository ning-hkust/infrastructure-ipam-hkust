package randoop;

/**
 * An object contract is an expression that represents an expected behavior of
 * an object or a collection of objects. More specifically, an object contract
 * is an expression that is expected to complete normally (throw no exceptions)
 * and to return <code>true</code>. Otherwise, the contract is violated.
 * <p>
 * Object contracts are only evaluated on non-null objects.
 * <p>
 * For example, the <code>randoop.EqualsToNull</code> contract represents the
 * expression <code>!o.equals(null)</code>, which is expected to return
 * <code>true</code> and throw no exceptions.
 */
public interface ObjectContract extends Expression {

}
