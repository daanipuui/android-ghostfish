package com.danielpuiu.ghostfish.annotations.processor;

import com.danielpuiu.ghostfish.GhostFish;
import com.danielpuiu.ghostfish.annotations.ApplicationScoped;
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@AutoService(Processor.class)
public class ApplicationScopedProcessor extends AbstractProcessor {

    private static final Set<String> SUPPORTED_ANNOTATIONS = initSupportedAnnotations();

    private Messager messager;

    private File beanFile;

    public ApplicationScopedProcessor() {
        beanFile = new File(GhostFish.getBeanFileName());
        beanFile.delete();
        beanFile.getParentFile().mkdirs();
    }

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

        Set<String> lines = readLines(beanFile);
        for (Element element : roundEnv.getElementsAnnotatedWith(ApplicationScoped.class)) {
            lines.add(element.toString());
        }

        writeLines(beanFile, lines);
        return true;
    }

    private Set<String> readLines(File file) {
        Set<String> lines = new HashSet<>();
        if (!file.exists()) {
             return lines;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            messager.printMessage(Kind.ERROR, e.getMessage());
        }

        return lines;
    }

    private void writeLines(File file, Set<String> lines) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line: lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            messager.printMessage(Kind.ERROR, e.getMessage());
        }
    }

    private static Set<String> initSupportedAnnotations() {
        Set<String> supportedAnnotations = new HashSet<>();
        supportedAnnotations.add(ApplicationScoped.class.getName());
        return Collections.unmodifiableSet(supportedAnnotations);
    }
}
