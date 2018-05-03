package com.danielpuiu.ghostfish;

import android.content.Context;
import android.util.Log;
import com.danielpuiu.ghostfish.annotations.ApplicationScoped;
import com.danielpuiu.ghostfish.annotations.Inject;
import com.danielpuiu.ghostfish.annotations.PostConstruct;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GhostFish {

    private static final String LOG_TAG = "android-ghostfish";
    private static final String BEAN_FILE_NAME = "app/src/main/assets/beans.txt";

    private static final GhostFish INSTANCE = new GhostFish();

    private final Map<Class<?>, Object> beans = new ConcurrentHashMap<>();

    private final Map<Class<?>, Constructor<?>> unresolvedBeans = new ConcurrentHashMap<>();

    private GhostFish() {
        // prevent instantiation
    }

    public static void bind(Context context) {
        INSTANCE.createBeans(context);
        INSTANCE.resolveBeans();

        INSTANCE.injectBeans();
        INSTANCE.postConstruct();
    }

    public static void injectBeans(Object targetBean) {
        for (Field field: targetBean.getClass().getDeclaredFields()) {
            int modifier = field.getModifiers();
            if (Modifier.isStatic(modifier) || Modifier.isFinal(modifier) || field.getAnnotation(Inject.class) == null) {
                continue;
            }

            Object bean = INSTANCE.beans.get(field.getType());
            Log.d(LOG_TAG, String.format("Found [%s] bean for [%s] type.", bean, field.getType()));
            if (bean == null) {
                continue;
            }

            setField(targetBean, field, bean);
        }
    }

    public static String getBeanFileName() {
        return BEAN_FILE_NAME;
    }

    private void createBeans(Context context) {
        ClassLoader classLoader = context.getClassLoader();
        try {
            for (String beanDefinition: getBeanClassNames(context)) {
                Class<?> cls = classLoader.loadClass(beanDefinition);
                if (!isApplicationScoped(cls)) {
                    continue;
                }

                Log.d(LOG_TAG, String.format("Creating bean for [%s] class.", beanDefinition));
                createBean(cls);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.d(LOG_TAG, e.getMessage());
        }
    }

    private List<String> getBeanClassNames(Context context) {
        List<String> classNames = new ArrayList<>();
        
        File file = new File(getBeanFileName());
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(context.getAssets().open(file.getName())))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                Log.d(LOG_TAG, String.format("Found bean class name [%s].", line));
                classNames.add(line);
            }
        } catch (IOException e) {
            Log.d(LOG_TAG, String.format("Cannot read asset file [%s].", getBeanFileName()));
        }

        return classNames;
    }

    private boolean isApplicationScoped(Class<?> cls) {
        return cls != null && cls.isAnnotationPresent(ApplicationScoped.class) &&
                !cls.isInterface() &&  !Modifier.isAbstract(cls.getModifiers());
    }

    private void createBean(Class<?> cls) throws NoSuchMethodException {
        Constructor<?> constructor = getConstructor(cls);
        if (canInstantiate(constructor)) {
            constructBean(cls, constructor);
            return;
        }

        unresolvedBeans.put(cls, constructor);
    }

    private boolean canInstantiate(Constructor<?> constructor) {
        for (Class<?> parameterClass: constructor.getParameterTypes()) {
            if (beans.get(parameterClass) == null) {
                return false;
            }
        }

        return true;
    }

    private void constructBean(Class<?> cls, Constructor<?> constructor) {
        try {
            boolean accessible = constructor.isAccessible();
            constructor.setAccessible(true);

            List<Object> arguments = new ArrayList<>();
            for (Class<?> parameterClass: constructor.getParameterTypes()) {
                arguments.add(beans.get(parameterClass));
            }

            Object bean = constructor.newInstance(arguments.toArray());
            mapBean(cls, bean);
            constructor.setAccessible(accessible);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            Log.d(LOG_TAG, e.getMessage());
        }
    }

    private Constructor<?> getConstructor(Class<?> cls) throws NoSuchMethodException {
        List<Constructor<?>> constructors = new ArrayList<>();
        for (Constructor<?> constructor: cls.getDeclaredConstructors()) {
            if (constructor.getAnnotation(Inject.class) != null) {
                constructors.add(constructor);
            }
        }

        if (constructors.size() > 1) {
            String message = String.format("[%s]: At most 1 constructor can be annotated with @Inject. Found [%d].", cls.getName(), constructors.size());
            throw new IllegalStateException(message);
        }

        if (constructors.size() == 1) {
            return constructors.get(0);
        }

        return cls.getDeclaredConstructor();
    }

    private void mapBean(Class<?> cls, Object bean) {
        beans.put(cls, bean);
        for (Class<?> declaredClass: cls.getInterfaces()) {
            beans.put(declaredClass, bean);
        }
    }

    private void injectBeans() {
        for (Object targetBean: beans.values()) {
            injectBeans(targetBean);
        }
    }

    private static void setField(Object object, Field field, Object value) {
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        try {
            Log.d(LOG_TAG, String.format("Setting field [%s] value [%s] on bean [%s].", field.getName(), value, object));
            field.set(object, value);
        } catch (IllegalAccessException e) {
            Log.d(LOG_TAG, e.getMessage());
        }

        field.setAccessible(accessible);
    }

    private void resolveBeans() {
        List<Class<?>> unresolvedBeanClasses = new ArrayList<>(unresolvedBeans.keySet());
        sortBeanByDependency(unresolvedBeanClasses);

        for (Class<?> cls: unresolvedBeanClasses) {
            Constructor<?> constructor = unresolvedBeans.get(cls);
            if (canInstantiate(constructor)) {
                constructBean(cls, constructor);
                continue;
            }

            Log.d(LOG_TAG, String.format("Cannot instantiate [%s] bean because of unsatisfied dependencies.", cls.getName()));
        }
    }

    private void sortBeanByDependency(List<Class<?>> unresolvedBeanClasses) {
        Collections.sort(unresolvedBeanClasses, new Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> cls1, Class<?> cls2) {
                Constructor<?> constructor1 = unresolvedBeans.get(cls1);
                if (Arrays.asList(constructor1.getParameterTypes()).contains(cls2)) {
                    return 1;
                }

                Constructor<?> constructor2 = unresolvedBeans.get(cls2);
                if (Arrays.asList(constructor2.getParameterTypes()).contains(cls1)) {
                    return -1;
                }

                return 0;
            }
        });
    }

    private void postConstruct() {
        for (Object bean: beans.values()) {
            for (Method method: bean.getClass().getDeclaredMethods()) {
                if (method.getAnnotation(PostConstruct.class) == null) {
                    continue;
                }

                try {
                    boolean accessible = method.isAccessible();
                    method.setAccessible(true);

                    Log.d(LOG_TAG, String.format("Running method [%s] on bean [%s].", method.getName(), bean));
                    method.invoke(bean);

                    method.setAccessible(accessible);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    Log.d(LOG_TAG, e.getMessage());
                }
            }
        }
    }
}
