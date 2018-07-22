package com.danielpuiu.ghostfish.annotations.processor;

import com.danielpuiu.ghostfish.annotations.ApplicationScoped;
import com.danielpuiu.ghostfish.annotations.Inject;
import org.junit.Test;

public class TestAnnotationProcessing {

    @Test
    public void testBeanAnnotation() {
    }
}

@ApplicationScoped
class Bean {
}

class Service {

    @Inject
    private Bean bean;
}
