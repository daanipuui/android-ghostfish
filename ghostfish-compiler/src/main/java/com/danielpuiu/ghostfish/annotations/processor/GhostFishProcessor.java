package com.danielpuiu.ghostfish.annotations.processor;

import com.danielpuiu.ghostfish.GhostFish;
import com.danielpuiu.ghostfish.annotations.ApplicationScoped;
import com.danielpuiu.ghostfish.annotations.Inject;
import com.google.auto.service.AutoService;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@AutoService(Processor.class)
public class GhostFishProcessor extends AbstractProcessor {

    private static final String INJECT_BEAN_METHOD = "injectBeans";
    private static final String FIELD_NAME = "this";

    private static final Set<String> SUPPORTED_ANNOTATIONS = initSupportedAnnotations();

    private boolean firstTime = true;

    private JavacElements trees;
    private TreeMaker treeMaker;

    private JCExpression injectBeanMethod;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        trees = (JavacElements) processingEnvironment.getElementUtils();
        treeMaker = TreeMaker.instance(((JavacProcessingEnvironment) processingEnvironment).getContext());
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
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!firstTime) {
            return false;
        }

        firstTime = false;
        processApplicationScopedAnnotation(roundEnv);
        processInjectAnnotation(roundEnv);

        return true;
    }

    private void processApplicationScopedAnnotation(RoundEnvironment roundEnv) {
        File file = new File(GhostFish.getBeanFileName());
        createBeanFolder(file);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Element element : roundEnv.getElementsAnnotatedWith(ApplicationScoped.class)) {
                writer.write(element.toString());
                writer.newLine();
            }

            writer.flush();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createBeanFolder(File file) {
        File folder = file.getParentFile();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException(String.format("Cannot create [%s] folder.", folder));
        }
    }

    private void processInjectAnnotation(RoundEnvironment roundEnv) {
        Set<Element> processedElements = new HashSet<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(Inject.class)) {
            if (element.getKind() != ElementKind.FIELD) {
                continue;
            }

            Element parent = element.getEnclosingElement();
            if (parent.getAnnotation(ApplicationScoped.class) == null && !processedElements.contains(parent)) {
                bindGhostFishToClass(parent);
                processedElements.add(parent);
            }
        }
    }

    private void bindGhostFishToClass(Element parent) {
        JCExpression method = createMethodIdentifier();
        JCExpression argument = treeMaker.Ident(trees.getName(FIELD_NAME));
        JCMethodInvocation methodInvocation = treeMaker.App(method, List.of(argument));

        JCBlock ghostFishCall = createInitializationBlock(methodInvocation);
        addBlockToParent(parent, ghostFishCall);
    }

    private void addBlockToParent(Element parent, JCBlock ghostFishCall) {
        JCClassDecl parentDeclaration = (JCClassDecl) trees.getTree(parent);
        List<JCTree> parentMembers = parentDeclaration.getMembers();

        ListBuffer<JCTree> tailBuffer = new ListBuffer<>();
        tailBuffer.addAll(parentMembers.tail);
        tailBuffer.add(ghostFishCall);
        parentMembers.setTail(tailBuffer.toList());
    }

    private JCBlock createInitializationBlock(JCMethodInvocation methodInvocation) {
        JCStatement statement = treeMaker.Exec(methodInvocation);
        return treeMaker.Block(0, List.of(statement));
    }

    private JCExpression createMethodIdentifier() {
        JCExpression method = treeMaker.Select(getGhostFishInjectBeanMethod(), trees.getName(INJECT_BEAN_METHOD));
        method.setType(Type.noType);
        return method;
    }

    private JCExpression getGhostFishInjectBeanMethod() {
        if (injectBeanMethod != null) {
            return injectBeanMethod;
        }

        JCExpression expression = null;
        for (String part: GhostFish.class.getName().split("\\.")) {
            Name name = trees.getName(part);
            expression = expression != null? treeMaker.Select(expression, name): treeMaker.Ident(trees.getName(part));
        }

        return injectBeanMethod = expression;
    }

    private static Set<String> initSupportedAnnotations() {
        Set<String> supportedAnnotations = new HashSet<>();
        supportedAnnotations.add(ApplicationScoped.class.getName());
        supportedAnnotations.add(Inject.class.getName());
        return Collections.unmodifiableSet(supportedAnnotations);
    }
}
