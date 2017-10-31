package com.hunterjdev.mockjdbc;

/**
 * <code>DefaultResultSetProvider</code> is used by default within
 * {@link MockDriver} when no other implementation is provided. Mock objects
 * are supplied sequentially in the same order as the supplied array.
 *
 * @see ResultSetProvider
 * @author James Hunter
 * @version 1.0
 *
 */
public class DefaultResultSetProvider implements ResultSetProvider {
    Object[] resultSets;
    int index;

    public DefaultResultSetProvider() {
    }

    /**
     * @see ResultSetProvider#getResultSet()
     */
    @Override
    public Object getResultSet() {
        Object current = null;
        if(resultSets != null) {
            if (index < resultSets.length) {
                current = resultSets[index];
                index++;
            }
        }
        return current;
    }

    /**
     * @see ResultSetProvider#setResultSets(java.lang.Object[])
     */
    @Override
    public void setResultSets(Object[] resultSets) {
        this.resultSets = resultSets;
        this.index = 0;
    }

}
