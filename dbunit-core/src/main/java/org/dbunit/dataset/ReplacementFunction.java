package org.dbunit.dataset;

public interface ReplacementFunction {
    String evaluate(String parameter) throws DataSetException;
}