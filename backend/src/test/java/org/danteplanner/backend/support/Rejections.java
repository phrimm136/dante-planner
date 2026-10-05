package org.danteplanner.backend.support;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.Problems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public final class Rejections {

    private Rejections() {
    }

    public static RejectionAssert assertRejected(ThrowingCallable call) {
        Throwable thrown = catchThrowable(call);
        assertThat(thrown).as("expected a rejection").isInstanceOf(DomainException.class);
        return new RejectionAssert((DomainException) thrown);
    }

    public static final class RejectionAssert extends AbstractAssert<RejectionAssert, DomainException> {

        private RejectionAssert(DomainException actual) {
            super(actual, RejectionAssert.class);
        }

        public RejectionAssert withCode(String code) {
            Map<String, Object> properties = actual.getBody().getProperties();
            Object published = properties == null ? null : properties.get(Problems.CODE);
            if (!code.equals(published)) {
                failWithMessage("expected the response code <%s>, but the body carries <%s>", code, published);
            }
            return this;
        }

        public RejectionAssert withViolation(String code) {
            PlannerValidationException validation = validation();
            List<ValidationError> subErrors = validation.getSubErrors();
            if (!code.equals(validation.getOriginalCode())
                    && subErrors.stream().noneMatch(error -> error.code().equals(code))) {
                failWithMessage("expected a violation <%s>, but the rejection carried <%s> with sub-errors %s",
                        code, validation.getOriginalCode(), subErrors);
            }
            return this;
        }

        public RejectionAssert withViolation(String code, String path) {
            List<ValidationError> subErrors = validation().getSubErrors();
            if (subErrors.stream().noneMatch(error -> error.code().equals(code) && error.path().equals(path))) {
                failWithMessage("expected a violation <%s> at path <%s>, but found %s", code, path, subErrors);
            }
            return this;
        }

        private PlannerValidationException validation() {
            if (!(actual instanceof PlannerValidationException validation)) {
                failWithMessage("a %s carries no violations; only a PlannerValidationException does",
                        actual.getClass().getSimpleName());
                throw new IllegalStateException("unreachable");
            }
            return validation;
        }
    }
}
