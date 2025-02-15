/*
 * Copyright 2002-2016 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.springtestdbunit.assertion;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.dbunit.Assertion;
import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.FilteredTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchColumnException;
import org.dbunit.dataset.filter.IColumnFilter;

/**
 * Implements non-strict database assertion strategy : compares data sets
 * ignoring all tables and columns which are not specified in expected data set
 * but possibly exist in actual data set.
 *
 * @author Mario Zagar
 * @author Sunitha Rajarathnam
 */
class NonStrictDatabaseAssertion implements DatabaseAssertion {

    @Override
    public void assertEquals(IDataSet expectedDataSet, IDataSet actualDataSet, List<IColumnFilter> columnFilters, List<String> ignoreCols)
            throws DatabaseUnitException {
        for (String tableName : expectedDataSet.getTableNames()) {
            ITable expectedTable = expectedDataSet.getTable(tableName);
            ITable actualTable = actualDataSet.getTable(tableName);
            assertEquals(expectedTable, actualTable, columnFilters, ignoreCols);
        }
    }

    @Override
    public void assertEquals(ITable expectedTable, ITable actualTable, List<IColumnFilter> columnFilters, List<String> ignoreCols)
            throws DatabaseUnitException {
        Set<String> ignoredColumns = getColumnsToIgnore(expectedTable.getTableMetaData(),
                actualTable.getTableMetaData(), columnFilters, ignoreCols);
        Assertion.assertEqualsIgnoreCols(expectedTable, actualTable,
                ignoredColumns.toArray(new String[0]));
    }

    private Set<String> getColumnsToIgnore(ITableMetaData expectedMetaData, ITableMetaData actualMetaData,
            List<IColumnFilter> columnFilters, List<String> ignoreCols) throws DataSetException {
        if (columnFilters.size() == 0 && ignoreCols.size() == 0) {
            return getColumnsToIgnore(expectedMetaData, actualMetaData);
        }
        Set<String> ignoredColumns = new LinkedHashSet<>();
        for (IColumnFilter filter : columnFilters) {
            FilteredTableMetaData filteredExpectedMetaData = new FilteredTableMetaData(expectedMetaData, filter);
            ignoredColumns.addAll(getColumnsToIgnore(filteredExpectedMetaData, actualMetaData));
        }
        for (String each : ignoreCols) {
            FilteredTableMetaData filteredExpectedMetaData = new FilteredTableMetaData(expectedMetaData,newIColumnFilter(expectedMetaData, each));
            ignoredColumns.addAll(getColumnsToIgnore(filteredExpectedMetaData, actualMetaData));
        }
        return ignoredColumns;
    }

    private IColumnFilter newIColumnFilter(ITableMetaData expectedMetaData, String columnName) {
        return new IColumnFilter() {
            @Override
            public boolean accept(String tableName, Column column) {
                boolean tableNameMatch = tableName.equalsIgnoreCase(expectedMetaData.getTableName());
                boolean columnNameMatch = columnName.equalsIgnoreCase(column.getColumnName());
                return !(tableNameMatch && columnNameMatch);
            }
        };
    }

    protected Set<String> getColumnsToIgnore(ITableMetaData expectedMetaData, ITableMetaData actualMetaData)
            throws DataSetException {
        Column[] allActualCols = actualMetaData.getColumns();

        Set<String> notExpected = new LinkedHashSet<>();

        for (Column column : allActualCols) {
            String columnName = column.getColumnName();
            if (!tableHasColumn(expectedMetaData, columnName)) {
                notExpected.add(columnName);
            }
        }

        return notExpected;
    }

    private boolean tableHasColumn(ITableMetaData tableMetaData, String columnName) throws DataSetException {
        boolean hasColumn1;
        try {
            tableMetaData.getColumnIndex(columnName);
            hasColumn1 = true;
        } catch (NoSuchColumnException e) {
            hasColumn1 = false;
        }
        return hasColumn1;
    }

}
