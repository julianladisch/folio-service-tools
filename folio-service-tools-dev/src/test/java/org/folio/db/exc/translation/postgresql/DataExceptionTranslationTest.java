package org.folio.db.exc.translation.postgresql;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import static org.folio.db.ErrorFactory.getCheckViolationErrorMap;
import static org.folio.db.ErrorFactory.getDataLengthMismatch;
import static org.folio.db.ErrorFactory.getErrorMapWithSqlStateNull;

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException;
import com.github.jasync.sql.db.postgresql.messages.backend.ErrorMessage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import org.folio.common.pf.PartialFunction;
import org.folio.db.exc.DataException;
import org.folio.db.exc.DatabaseException;
import org.folio.test.junit.TestStartLoggingRule;

public class DataExceptionTranslationTest {

  @Rule
  public TestRule startLogger = TestStartLoggingRule.instance();

  @Test
  public void shouldReturnDatabaseExceptionWithDataExceptionCode(){
    GenericDatabaseException exception = new GenericDatabaseException(new ErrorMessage(getDataLengthMismatch()));
    final PartialFunction<GenericDatabaseException, DatabaseException> asPartial = DataExceptionTranslation.asPartial();
    final DatabaseException apply = asPartial.apply(exception);

    assertThat(apply.getSqlState(), equalTo(PSQLState.STRING_DATA_RIGHT_TRUNCATION.getCode()));
  }

  @Test
  public void shouldReturnTrueWhenExceptionIsDataException(){
    GenericDatabaseException exception = new GenericDatabaseException(new ErrorMessage(getDataLengthMismatch()));
    final boolean isDataException = new DataExceptionTranslation.TPredicate().test(exception);

    assertTrue(isDataException);
  }

  @Test
  public void shouldReturnFalseWhenExceptionIsNotDataException(){
    GenericDatabaseException exception = new GenericDatabaseException(new ErrorMessage(getCheckViolationErrorMap()));
    final boolean isDataException = new DataExceptionTranslation.TPredicate().test(exception);

    assertFalse(isDataException);
  }

  @Test
  public void shouldReturnFalseWhenExceptionIsNull(){
    GenericDatabaseException exception = new GenericDatabaseException(new ErrorMessage(getErrorMapWithSqlStateNull()));
    final boolean isDataException = new DataExceptionTranslation.TPredicate().test(exception);

    assertFalse(isDataException);
  }

  @Test
  public void shouldReturnDataExceptionWithSqlStateWhenExceptionIsDataException(){
    GenericDatabaseException exception = new GenericDatabaseException(new ErrorMessage(getDataLengthMismatch()));
    final DataException resultException = new DataExceptionTranslation.TFunction().apply(exception);

    assertThat(resultException.getSqlState(), equalTo(PSQLState.STRING_DATA_RIGHT_TRUNCATION.getCode()));
  }

  @Test
  public void shouldReturnDataExceptionWithNoFieldsWhenExceptionIsNull(){
    GenericDatabaseException exception = new GenericDatabaseException(new ErrorMessage(getErrorMapWithSqlStateNull()));
    final DataException resultException = new DataExceptionTranslation.TFunction().apply(exception);

    assertNull(resultException.getSqlState());
    assertNull(resultException.getMessage());
  }
}
