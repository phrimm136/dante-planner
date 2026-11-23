package org.danteplanner.backend.shared.config;

import org.springframework.beans.PropertyAccessException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.support.ExtendedServletRequestDataBinder;

@ControllerAdvice
public class ModelBindingAdvice {

    @InitBinder
    void bindRequestParametersOnly(ExtendedServletRequestDataBinder binder) {
        binder.setHeaderPredicate(name -> false);
        binder.setBindingErrorProcessor(new TypeMismatchEscapes());
    }

    private static final class TypeMismatchEscapes extends DefaultBindingErrorProcessor {

        @Override
        public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
            throw ex;
        }
    }
}
