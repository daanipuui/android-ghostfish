package com.danielpuiu.ghostfish.annotations.processor;

import com.danielpuiu.ghostfish.annotations.ApplicationScoped;
import com.danielpuiu.ghostfish.annotations.Inject;
import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@AutoService(Processor.class)
public class InjectProcessor extends AbstractProcessor {

    private static final Set<String> SUPPORTED_ANNOTATIONS = initSupportedAnnotations();

    private Messager messager;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return true;
        }

        for (Element element: roundEnv.getElementsAnnotatedWith(Inject.class)) {
            if (element.getEnclosingElement().getAnnotation(ApplicationScoped.class) != null) {
                continue;
            }

            messager.printMessage(Kind.WARNING, "No GhostFish bind call found");
        }

        return true;
    }

    private static Set<String> initSupportedAnnotations() {
        Set<String> supportedAnnotations = new HashSet<>();
        supportedAnnotations.add(Inject.class.getName());
        return Collections.unmodifiableSet(supportedAnnotations);
    }
}

