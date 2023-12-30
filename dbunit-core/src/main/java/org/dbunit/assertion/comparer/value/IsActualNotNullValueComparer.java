package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * {@link ValueComparer} implementation that verifies actual value is not null.
 * Note, ignores any expected value.
 *
 * @author Jeff Jensen
 * @since 2.7.3
 */
public class IsActualNotNullValueComparer extends ValueComparerTemplateBase
{
    private static final String ACTUAL_VALUE_IS_NULL =
            "Actual value is null (ignores expected value)";

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        return actualValue != null;
    }

    protected String makeFailMessage()
    {
        return ACTUAL_VALUE_IS_NULL;
    }

    @Override
    protected String getFailPhrase()
    {
        // ignored
        return null;
    }
}
