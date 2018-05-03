# android-ghostfish [ ![Download](https://api.bintray.com/packages/daanipuui/android/android-ghostfish/images/download.svg) ](https://bintray.com/daanipuui/android/android-ghostfish/_latestVersion)
Dependency injection container for Android

## 1. Annotations

- `ApplicationScoped` - specifies that a bean is application scoped.
- `Inject` - injectable instance fields or constructors.
- `PostConstruct` - method that needs to be run after instantiating an application scoped bean.

## 2. Dependency injection

Using annotation processors, GhostFish compiles a list of application scoped beans in *asset/beans.txt* file.

During this stage, it also adds code allowing dependency injection to every non-bean class containing at least 1 `@Inject` annotation.

## 3. Example

### 3.1 Add GhostFish dependency to your gradle file:

```
implementation "com.danielpuiu:ghostfish:1.0.0"
annotationProcessor "com.danielpuiu:ghostfish-compiler:1.0.0"
```

### 3.2 Add GhostFish bind call to your application

Subclass Android application and add the following code to `onCreate` method:

```
@Override
public void onCreate() {
    super.onCreate();

    GhostFish.bind(this);
}
```

### 3.3 Proguard

If you use Proguard and minified, then make sure to add the following rule to your proguard rules file:

```
-keep @com.danielpuiu.ghostfish.annotations.ApplicationScoped class * {*;}
```

### 3.4 Sample code

#### 3.4.1 Service class

```
@ApplicationScoped
public class SampleService {

    public String helloWorld() {
        return "Hello, world!";
    }

    @PostConstruct
    private void init() {
        // code
    }
}
```

#### 3.4.2 Activity class

```
public class MainActivity extends AppCompatActivity {

    @Inject
    private SampleService sampleService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ...
        TextView textView = findViewById(R.id.message);
        textView.setText(sampleService.helloWorld());
        ...
    }
}
```

# 4 Developed By

Daniel PUIU

# 5 License

Copyright 2018 Daniel PUIU

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
