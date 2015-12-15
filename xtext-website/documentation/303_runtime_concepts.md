---
layout: documentation
title: Runtime Concepts
part: Reference Documentation
---

# {{page.title}} {#runtime-concepts}

Xtext itself and every language infrastructure developed with Xtext is configured and wired-up using [dependency injection](302_configuration.html#dependency-injection). Xtext may be used in different environments which introduce different constraints. Especially important is the difference between OSGi managed containers and plain vanilla Java programs. To honor these differences Xtext uses the concept of [ISetup]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/ISetup.java)-implementations in normal mode and uses Eclipse's extension mechanism when it should be configured in an OSGi environment.

## Runtime Setup (ISetup) {#runtime-setup}

For each language there is an implementation of [ISetup]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/ISetup.java) generated. It implements a method called `createInjectorAndDoEMFRegistration()`, which can be called to do the initialization of the language infrastructure.

**Caveat:** The [ISetup]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/ISetup.java) class is intended to be used for runtime and for unit testing, only. if you use it in a Equinox scenario, you will very likely break the running application because entries to the global registries will be overwritten.

The setup method returns an [Injector]({{site.javadoc.guice}}/com/google/inject/Injector.html), which can further be used to obtain a parser, etc. It also registers the [Factory]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java) and generated [EPackages]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EPackage.java) to the respective global registries provided by EMF. So basically after having run the setup and you can start using EMF API to load and store models of your language.

## Setup within Eclipse-Equinox (OSGi) {#equinox-setup}

Within Eclipse we have a generated *Activator*, which creates a Guice [Injector]({{site.javadoc.guice}}/com/google/inject/Injector.html) using the [modules](302_configuration.html#guicemodules). In addition an [IExecutableExtensionFactory]({{site.javadoc.eclipse-platform}}/org/eclipse/core/runtime/IExecutableExtensionFactory.html) is generated for each language, which is used to create [IExecutableExtensions]({{site.javadoc.eclipse-platform}}/org/eclipse/core/runtime/IExecutableExtension.html). This means that everything which is created via extension points is managed by Guice as well, i.e. you can declare dependencies and get them injected upon creation.

The only thing you have to do in order to use this factory is to prefix the class with the factory *MyDslExecutableExtensionFactory* name followed by a colon.

```xml
<extension point="org.eclipse.ui.editors">
  <editor
    class="<MyDsl>ExecutableExtensionFactory:
      org.eclipse.xtext.ui.editor.XtextEditor"
    contributorClass=
      "org.eclipse.ui.editors.text.TextEditorActionContributor"
    default="true"
    extensions="mydsl"
    id="org.eclipse.xtext.example.MyDsl"
    name="MyDsl Editor">
  </editor>
</extension>
```

## Logging

Xtext uses Apache's log4j for logging. It is configured using files named *log4j.properties*, which are looked up in the root of the Java class path. If you want to change or provide configuration at runtime (i.e. non-OSGi), all you have to do is putting such a *log4j.properties* in place and make sure that it is not overridden by other *log4j.properties* in previous class path entries.

In OSGi you provide configuration by creating a fragment for *org.apache.log4j*. In this case you need to make sure that there is not any second fragment contributing a *log4j.properties* file.

## Code Generation / Compilation {#code-generation}

Once you have a language you probably want to do something with it. There are two options, you can either write an interpreter that inspects the AST and does something based on that or you translate your language to another programming language or configuration files. In this section we're going to show how to implement a code generator for an Xtext-based language.

### IGenerator

If you go with the default MWE workflow for your language and you haven't used Xbase, than you'll be provided with a callback stub that implements [IGenerator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/generator/IGenerator.java). It has one method that is called from the builder infrastructure whenever a DSL file has changed or should be translated otherwise. The two parameters passed in to this method are:

*   The resource to be processed
*   An instance of [IFileSystemAccess]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/generator/IFileSystemAccess.java)

The [IFileSystemAccess]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/generator/IFileSystemAccess.java) API abstracts over the different file systems the code generator my run over. These are typically Eclipse's file system, when the code generator is triggered from within the incremental build infrastructure in Eclipse, and `java.io.File` when the code generator is executed outside Eclipse, say in a headless build.

A very simple implementation of a code generator for the [example statemachine language](301_grammarlanguage.html#statemachine) introduced earlier could be the following:

```xtend
class StatemachineGenerator implements IGenerator {

  override void doGenerate(Resource resource, IFileSystemAccess fsa) {
    fsa.generateFile("relative/path/AllTheStates.txt", '''
      «FOR state : resource.allContents.filter(State).toIterable»
        State «state.name»
      «ENDFOR»
    ''')
  }
}
```

We use Xtend for implementing code generators as it is much better suited for that task then Java (or any other language on the planet :-)). Please refer to the [Xtend documentation](http://www.xtend-lang.org) for further details. For Java developers it's extremely easy to learn, as the basics are similar and you only need to learn the additional powerful concepts.

### Output Configurations

You don't want to deal with platform or even installation dependent paths in your code generator, rather you want to be able to configure the code generator with some basic outlet roots where the different generated files should be placed under. This is what output configurations are made for.

By default every language will have a single outlet, which points to `<project-root>/src-gen/`. The files that go here are treated as fully derived and will be erased by the compiler automatically when a new file should be generated. If you need additional outlets or want to have a different default configuration, you need to implement the interface [IOutputConfigurationProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/generator/IOutputConfigurationProvider.java). It's straight forward to understand and the default implementation gives you a good idea about how to implement it.

With this implementation you lay out the basic defaults which can be changed by users on a workspace or per project level using the preferences.

## Validation {#validation}

Static analysis or validation is one of the most interesting aspects when developing a programming language. The users of your languages will be grateful if they get informative feedback as they type. In Xtext there are basically three different kinds of validation.

### Automatic Validation

Some implementation aspects (e.g. the grammar, scoping) of a language have an impact on what is required for a document or semantic model to be valid. Xtext automatically takes care of this.

#### Lexer/Parser: Syntactical Validation {#syntactical-validation}

The syntactical correctness of any textual input is validated automatically by the parser. The error messages are generated by the underlying parser technology. One can use the [ISyntaxErrorMessageProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parser/antlr/ISyntaxErrorMessageProvider.java)-API to customize this messages. Any syntax errors can be retrieved from the Resource using the common EMF API:

*   [`Resource.getErrors()`]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java)
*   [`Resource.getWarnings()`]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java)

#### Linker: Cross-link Validation {#linking-validation}

Any broken cross-links can be checked generically. As cross-link resolution is done lazily (see [linking](#linking)), any broken links are resolved lazily as well. If you want to validate whether all links are valid, you will have to navigate through the model so that all installed EMF proxies get resolved. This is done automatically in the editor.

Similar to syntax errors, any unresolvable cross-links will be reported and can be obtained through:

*   [`Resource.getErrors()`]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java)
*   [`Resource.getWarnings()`]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java)

#### Serializer: Concrete Syntax Validation {#concrete-syntax-validation}

The [IConcreteSyntaxValidator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/IConcreteSyntaxValidator.java) validates all constraints that are implied by a grammar. Meeting these constraints for a model is mandatory to be serialized.

Example:

```xtext
MyRule:
  ({MySubRule} "sub")? (strVal+=ID intVal+=INT)*;
```

This implies several constraints:

1.  Types: only instances of *MyRule* and *MySubRule* are allowed for this rule. Subtypes are prohibited, since the parser never instantiates unknown subtypes.
1.  Features: In case the *MyRule* and *MySubRule* have [EStructuralFeatures]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EStructuralFeature.java) besides *strVal* and *intVal*, only *strVal* and *intVal* may have [non-transient values](#transient-values).
1.  Quantities: The following condition must be true: `strVal.size() == intVal.size()`.
1.  Values: It must be possible to [convert all values](#value-converter) to valid tokens for terminal rule *STRING*. The same is true for *intVal* and *INT*.

The typical use case for the concrete syntax validator is validation in non-Xtext-editors that, however, use an [XtextResource]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/XtextResource.java). This is, for example, the case when combining GMF and Xtext. Another use case is when the semantic model is modified "manually" (not by the parser) and then serialized again. Since it is very difficult for the serializer to provide [meaningful error messages](#parse-tree-constructor), the concrete syntax validator is executed by default before serialization. A textual Xtext editor itself is *not* a valid use case. Here, the parser ensures that all syntactical constraints are met. Therefore, there is no value in additionally running the concrete syntax validator.

There are some limitations to the concrete syntax validator which result from the fact that it treats the grammar as declarative, which is something the parser doesn't always do.

*   Grammar rules containing assigned actions (e.g. `{MyType.myFeature=current}` are ignored. Unassigned actions (e.g. `{MyType}`), however, are supported.
*   Grammar rules that delegate to one or more rules containing assigned actions via unassigned rule calls are ignored.
*   Orders within list-features can not be validated. e.g. `Rule: (foo+=R1 foo+=R2)*` implies that *foo* is expected to contain instances of *R1* and *R2* in an alternating order.

To use concrete syntax validation you can let Guice inject an instance of [IConcreteSyntaxValidator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/IConcreteSyntaxValidator.java) and use it directly. Furthermore, there is an [adapter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/impl/ConcreteSyntaxEValidator.java) which allows to use the concrete syntax validator as an [EValidator]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EValidator.java). You can, for example, enable it in your runtime module, by adding:

```java
@SingletonBinding(eager = true)
public Class<? extends ConcreteSyntaxEValidator>
      bindConcreteSyntaxEValidator() {
  return ConcreteSyntaxEValidator.class;
}
```

To customize error messages please see [IConcreteSyntaxDiagnosticProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/IConcreteSyntaxDiagnosticProvider.java) and subclass [ConcreteSyntaxDiagnosticProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/impl/ConcreteSyntaxDiagnosticProvider.java).

### Custom Validation {#custom-validation}

In addition to the afore mentioned kinds of validation, which are more or less done automatically, you can specify additional constraints specific for your Ecore model. We leverage existing [EMF API]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EValidator.java) and have put some convenience stuff on top. Basically all you need to do is to make sure that an [EValidator]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EValidator.java) is registered for your [EPackage]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EPackage.java). The [Registry]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EValidator.java) can only be filled programmatically. That means contrary to the [Registry]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EPackage.java) and the [Registry]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java) there is no Equinox extension point to populate the validator registry.

For Xtext we provide a [generator fragment](302_configuration.html#generator-fragment) for the convenient Java-based [EValidator]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EValidator.java) API. Just add the following fragment to your generator configuration and you are good to go:

```mwe2
fragment =
  org.eclipse.xtext.generator.validation.JavaValidatorFragment {}
```

The generator will provide you with two Java classes. An abstract class generated to *src-gen/* which extends the library class [AbstractDeclarativeValidator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/AbstractDeclarativeValidator.java). This one just registers the [EPackages]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EPackage.java) for which this validator introduces constraints. The other class is a subclass of that abstract class and is generated to the *src/* folder in order to be edited by you. That is where you put the constraints in.

The purpose of the [AbstractDeclarativeValidator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/AbstractDeclarativeValidator.java) is to allow you to write constraints in a declarative way - as the class name already suggests. That is instead of writing exhaustive if-else constructs or extending the generated EMF switch you just have to add the [Check]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/Check.java) annotation to any method and it will be invoked automatically when validation takes place. Moreover you can state for what type the respective constraint method is, just by declaring a typed parameter. This also lets you avoid any type casts. In addition to the reflective invocation of validation methods the [AbstractDeclarativeValidator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/AbstractDeclarativeValidator.java) provides a couple of convenient assertions.

The Check annotation has a parameter that can be used to declare when a check should be run, {{FAST}} will be run whenever a file is modified, {{NORMAL}} checks will run when saving the file, and {{EXPENSIVE}} checks are run when explicitly validating the file via the menu option.

All in all this is very similar to how JUnit 4 works. Here is an example:

```java
public class DomainmodelJavaValidator
  extends AbstractDomainmodelJavaValidator {

  @Check(FAST)
  public void checkTypeNameStartsWithCapital(Type type) {
    if (!Character.isUpperCase(type.getName().charAt(0)))
      warning("Name should start with a capital",
        DomainmodelPackage.TYPE__NAME);
  }
}
```

You can also implement quick fixes for individual validation errors and warnings. See the [section on quick fixes](304_ide_concepts.html#quick-fixes) for details.

### Validating Manually

As noted above, Xtext uses EMF's [EValidator]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EValidator.java) API to register validators. You can run the validators on your model programmatically using EMF's [Diagnostician]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/util/Diagnostician.java), e.g.

```java
EObject myModel = myResource.getContents().get(0);
Diagnostic diagnostic = Diagnostician.INSTANCE.validate(myModel);
switch (diagnostic.getSeverity()) {
  case Diagnostic.ERROR:
    System.err.println("Model has errors: ",diagnostic);
    break;
  case Diagnostic.WARNING:
    System.err.println("Model has warnings: ",diagnostic);
}
```

### Test Validators {#test-validators}

If you have implemented your validators by extending [AbstractDeclarativeValidator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/AbstractDeclarativeValidator.java), there are helper classes which assist you when testing your validators.

Testing validators typically works as follows:

1.  The test creates some models which intentionally violate some constraints.
1.  The test runs some chosen [@Check-methods]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/Check.java) from the validator.
1.  The test asserts whether the [@Check-methods]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/Check.java) have raised the expected warnings and errors.

To create models, you can either use EMF's [ResourceSet]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/ResourceSet.java) to load models from your hard disk or you can utilize the *MyDslFactory* that EMF generates for each [EPackage]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EPackage.java), to construct the tested model elements manually. While the first option has the advantages that you can edit your models in your textual concrete syntax, the second option has the advantage that you can create partial models.

To run the [@Check-methods]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/Check.java) and ensure they raise the intended errors and warnings, you can utilize [ValidatorTester]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/validation/ValidatorTester.java) as shown by the following example:

Validator:

```java
public class MyLanguageValidator extends AbstractDeclarativeValidator {
  @Check
  public void checkFooElement(FooElement element) {
    if(element.getBarAttribute().contains("foo"))
      error("Only Foos allowed", element,
        MyLanguagePackage.FOO_ELEMENT__BAR_ATTRIBUTE, 101);
  }
}
```

JUnit-Test:

```java
public class MyLanguageValidatorTest extends AbstractXtextTests {

  private ValidatorTester<MyLanguageValidator> tester;

  @Override
  public void setUp() {
    with(MyLanguageStandaloneSetup.class);
    MyLanguageValidator validator = get(MyLanguageValidator.class);
    tester = new ValidatorTester<TestingValidator>(validator);
  }

  public void testError() {
    FooElement model = MyLanguageFactory.eINSTANCE.createFooElement()
    model.setBarAttribute("barbarbarbarfoo");

    tester.validator().checkFooElement(model);
    tester.diagnose().assertError(101);
  }

  public void testError2() {
    FooElement model = MyLanguageFactory.eINSTANCE.createFooElement()
    model.setBarAttribute("barbarbarbarfoo");

    tester.validate(model).assertError(101);
  }
}
```

This example uses JUnit 3, but since the involved classes from Xtext have no dependency on JUnit whatsoever, JUnit 4 and other testing frameworks will work as well. JUnit runs the `setUp()`-method before each test case and thereby helps to create some common state. In this example, the validator is instantiated by means of Google Guice. As we inherit from the [AbstractXtextTests]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/AbstractXtextTests.java) there are a plenty of useful methods available and the state of the global EMF singletons will be restored in the method `tearDown()`. Afterwards, the [ValidatorTester]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/validation/ValidatorTester.java) is created and parameterized with the actual validator. It acts as a wrapper for the validator, ensures that the validator has a valid state and provides convenient access to the validator itself (`tester.validator()`) as well as to the utility classes which assert diagnostics created by the validator (`tester.diagnose()`). Please be aware that you have to call `validator()` before you can call `diagnose()`. However, you can call `validator()` multiple times in a row.

While `validator()` allows to call the validator's [@Check-methods]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/Check.java) directly, `validate(model)` leaves it to the framework to call the applicable [@Check-methods]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/Check.java). However, to avoid side-effects between tests, it is recommended to call the [@Check-methods]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/Check.java) directly.

`diagnose()` and `validate(model)` return an object of type [AssertableDiagnostics]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/validation/AssertableDiagnostics.java) which provides several *assert*-methods to verify whether the expected diagnostics are present:

*   `assertError(int code)`: There must be one diagnostic with severity ERROR and the supplied error code.
*   `assertErrorContains(String messageFragment)`: There must be one diagnostic with severity ERROR and its message must contain *messageFragment*.
*   `assertError(int code, String messageFragment)`: Verifies severity, error code and messageFragment.
*   `assertWarning(...)`: This method is available for the same combination of parameters as `assertError()`.
*   `assertOK()`: Expects that no diagnostics (errors, warnings etc.) have been raised.
*   `assertDiagnostics(int severity, int code, String messageFragment)`: Verifies severity, error code and messageFragment.
*   `assertAll(DiagnosticPredicate... predicates)`: Allows to describe multiple diagnostics at the same time and verifies that all of them are present. Class [AssertableDiagnostics]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/validation/AssertableDiagnostics.java) contains static `error()` and `warning()` methods which help to create the needed [DiagnosticPredicate]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/validation/AssertableDiagnostics.java). Example: `assertAll(error(123), warning("some part of the message"))`.
*   `assertAny(DiagnosticPredicate predicate)`: Asserts that a diagnostic exists which matches the predicate.

## Linking {#linking}

The linking feature allows for specification of cross-references within an Xtext grammar. The following things are needed for the linking:

1.  declaration of a cross-link in the grammar (at least in the Ecore model)
1.  specification of linking semantics (usually provided via the [scoping API](#scoping))

### Declaration of Cross-links

In the grammar a cross-reference is specified using square brackets.

```xtext
CrossReference :
  '[' type=ReferencedEClass ('|' terminal=CrossReferenceTerminal)? ']'
;
```

Example:

```xtext
ReferringType :
  'ref' referencedObject=[Entity|STRING]
;
```

The [Ecore model inference](301_grammarlanguage.html#metamodel-inference) would create an [EClass]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EClass.java) *ReferringType* with an [EReference]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EReference.java) *referencedObject* of type *Entity* with its containment property set to `false`. The referenced object would be identified either by a *STRING* and the surrounding information in the current context (see [scoping](#scoping)). If you do not use `generate` but `import` an existing Ecore model, the class *ReferringType* (or one of its super types) would need to have an [EReference]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EReference.java) of type *Entity* (or one of its super types) declared. Also the [EReference's]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EReference.java) containment and container properties needs to be set to `false`.

### Default Runtime Behavior (Lazy Linking) {#lazy-linking}

Xtext uses lazy linking by default and we encourage users to stick to this because it provides many advantages. One of which is improved performance in all scenarios where you don't have to load the whole closure of all transitively referenced resources. Furthermore it automatically solves situations where one link relies on other links. Though cyclic linking dependencies are not supported by Xtext at all.

When parsing a given input string, say

`ref Entity01`

the [LazyLinker]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/linking/lazy/LazyLinker.java) first creates an EMF proxy and assigns it to the corresponding [EReference]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EReference.java). In EMF a proxy is described by a [URI]({{site.src.emf}}/plugins/org.eclipse.emf.common/src/org/eclipse/emf/common/util/URI.java), which points to the real [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java). In the case of lazy linking the stored [URI]({{site.src.emf}}/plugins/org.eclipse.emf.common/src/org/eclipse/emf/common/util/URI.java) comprises of the context information given at parse time, which is the [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) containing the cross-reference, the actual [EReference]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EReference.java), the index (in case it's a multi-valued cross-reference) and the string which represented the cross-link in the concrete syntax. The latter usually corresponds to the name of the referenced [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java). In EMF a [URI]({{site.src.emf}}/plugins/org.eclipse.emf.common/src/org/eclipse/emf/common/util/URI.java) consists of information about the resource the [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) is contained in as well as a so called fragment part, which is used to find the [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) within that resource. When an EMF proxy is resolved, the current [ResourceSet]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/ResourceSet.java) is asked. The resource set uses the first part to obtain (i.e. load if it is not already loaded) the resource. Then the resource is asked to return the [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) based on the fragment in the URI. The actual cross-reference resolution is done by [LazyLinkingResource.getEObject(String)]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/linking/lazy/LazyLinkingResource.java) which receives the fragment and delegates to the implementation of the [ILinkingService]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/linking/ILinkingService.java). The default implementation in turn delegates to the [scoping API](#scoping).

A [simple implementation]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/linking/impl/DefaultLinkingService.java) of the linking service is shipped with Xtext and used for any grammar per default. Usually any necessary customization of the linking behavior can best be described using the [scoping API](#scoping).

## Scoping {#scoping}

Using the scoping API one defines which elements are referable by a given reference. For instance, using the introductory example (Fowler's state machine language) a transition contains two cross-references: one to a declared event and one to a declared state.

Example:

```fowlerexample
events
  nothingImportant  MYEV
end

state idle
  nothingImportant => idle
end
```

The grammar rule for transitions looks like this:

```xtext
Transition :
  event=[Event] '=>' state=[State];
```

The grammar declares that for the reference *event* only instances of the type *Event* are allowed and that for the EReference *state* only instances of type *State* can be referenced. However, this simple declaration doesn't say anything about where to find the states or events. That is the duty of scopes.

An [IScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IScopeProvider.java) is responsible for providing an [IScope]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IScope.java) for a given context [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) and [EReference]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EReference.java). The returned [IScope]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IScope.java) should contain all target candidates for the given object and cross-reference.

```java
public interface IScopeProvider {

  /**
   * Returns a scope for the given context. The scope
   * provides access to the compatible visible EObjects
   * for a given reference.
   *
   * @param context the element from which an element shall be
   *        referenced
   * @param reference the reference to be used to filter the
   *        elements.
   * @return {@link IScope} representing the inner most
   *         {@link IScope} for the passed context and reference.
   *         Note for implementors: The result may not be
   *         <code>null</code>. Return
   *         <code>IScope.NULLSCOPE</code> instead.
   */
  IScope getScope(EObject context, EReference reference);

}
```

A single [IScope]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IScope.java) represents an element of a linked list of scopes. That means that a scope can be nested within an outer scope. Each scope works like a symbol table or a map where the keys are strings and the values are so called [IEObjectDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java), which is effectively an abstract description of a real [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java). In order to create IEObjectDescriptions for your model elements, the class [Scopes]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/Scopes.java) is very useful.

To have a concrete example, let's deal with the following simple grammar.

```xtext
grammar org.xtext.example.mydsl.MyScopingDsl with
                                      org.eclipse.xtext.common.Terminals

generate myDsl "http://www.xtext.org/example/mydsl/MyScopingDsl"

Root:
    elements+=Element;

Element:
    'element' name=ID ('extends' superElement=[Element])?;

```

If you want to define the scope for the superElement cross-reference, the following code is one way to go.

```java
  IScope getScope(EObject context, EReference reference) {
      // We want to define the Scope for the Element's superElement cross-reference
      if(context instanceof Element
          && reference == MyDslPackage.Literals.ELEMENT__SUPER_ELEMENT){
        // Collect a list of candidates by going through the model
        // EcoreUtil2 provides useful functionality to do that
        // For example searching for all elements within the root Object's tree
        EObject rootElement = EcoreUtil2.getRootContainer(context);
        List<Element> candidates = EcoreUtil2.getAllContentsOfType(rootElement, Element.class);
        // Scopes.scopeFor creates IEObjectDescriptions and puts them into an IScope instance
        IScope scope = Scopes.scopeFor(candidates);
        return scope;
      }
      return super.getScope(context, reference);
  }
```

There are different useful implementations for IScopes shipped with Xtext. We want to mention only some of them here.
The [MapBasedScope]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/impl/MapBasedScope.java) comes with the efficiency of a map to look up a certain name. If you prefer to deal with Multimaps the [MultimapBasedScope]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/impl/MultimapBasedScope.java) should work for you. For situations where some elements should be filtered out of an existing scope, the [FilteringScope]({{site.src.xtext}}/plugins/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/impl/FilteringScope.java) is the right way to go. As scopes can be nested, we strongly recommend to use FilteringScope only for leaf scopes without nested scopes.

Coming back to our example, one possible scenario for the FilteringScope could be to exclude the context element from the list of candidates as it should not be a super-element of itself.

```java
  IScope getScope(final EObject context, EReference reference) {
      if(context instanceof Element
          && reference == MyDslPackage.Literals.ELEMENT__SUPER_ELEMENT){
        EObject rootElement = EcoreUtil2.getRootContainer(context);
        List<Element> candidates = EcoreUtil2.getAllContentsOfType(rootElement, Element.class);
        IScope existingScope = Scopes.scopeFor(candidates);
        // Scope that filters out the context element from the candidates list
        IScope filteredScope = new FilteringScope(existingScope,
                                                  new Predicate<IEObjectDescription>() {
            public boolean apply(IEObjectDescription input) {
              return input.getEObjectOrProxy() != context;
            }
        });
        return filteredScope;
      }
      return super.getScope(context, reference);
  }

```

### Global Scopes and Resource Descriptions {#global-scopes}

In the state machine example we don't have references across model files. Neither is there a concept like a namespace which would make scoping a bit more complicated. Basically, every *State* and every *Event* declared in the same resource is visible by their name. However, in the real world things are most likely not that simple: What if you want to reuse certain declared states and events across different state machines and you want to share those as library between different users? You would want to introduce some kind of cross-resource reference.

Defining what is visible from outside the current resource is the responsibility of global scopes. As the name suggests, global scopes are provided by instances of the [IGlobalScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IGlobalScopeProvider.java). The data structures (called index) used to store its elements are described in the next section.

#### Resource and EObject Descriptions {#resource-descriptions}

In order to make states and events of one file referable from another file, you need to export them as part of a so called [IResourceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java).

A [IResourceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) contains information about the resource itself, which primarily its [URI]({{site.src.emf}}/plugins/org.eclipse.emf.common/src/org/eclipse/emf/common/util/URI.java), a list of exported [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java)s in the form of [IEObjectDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java)s, as well as information about outgoing cross-references and qualified names it references. The cross-references contain only resolved references, while the list of imported qualified names also contains the names that couldn't be resolved. This information is leveraged by Xtext's indexing infrastructure in order to compute the transitive hull of dependent resources.

For users, and especially in the context of scoping, the most important information is the list of exported [EObjects]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java). An [IEObjectDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java) stores the [URI]({{site.src.emf}}/plugins/org.eclipse.emf.common/src/org/eclipse/emf/common/util/URI.java) of the actual [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java), its [QualifiedName]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/QualifiedName.java), as well as its [EClass]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EClass.java). In addition one can export arbitrary information using the *user data* map. The following diagram gives an overview on the description classes and their relationships.

![The data model of Xtext's index](images/index_datamodel.png)

A language is configured with default implementations of [IResourceDescription.Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) and [DefaultResourceDescriptionStrategy]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/impl/DefaultResourceDescriptionStrategy.java), which are responsible to compute the list of exported [IEObjectDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java)s. The Manager iterates over the whole EMF model for each [Resource]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java) and asks the ResourceDescriptionStrategy to compute an IEObjectDescription for each [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java). The ResourceDescriptionStrategy applies the `getQualifiedName(EObject obj)` from [IQualifiedNameProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/IQualifiedNameProvider.java) on the object, and if it has a qualified name an [IEObjectDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java) is created and passed back to the Manager which adds it to the list of exported objects. If an EObject doesn't have a qualified name, the element is considered to be not referable from outside the resource and consequently not indexed. If you don't like this behavior, you can implement and bind your own implementation of [IDefaultResourceDescriptionStrategy]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IDefaultResourceDescriptionStrategy.java).

There are also two different default implementations of [IQualifiedNameProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/IQualifiedNameProvider.java). Both work by looking up an [EAttribute]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EAttribute.java) '*name*'. The [SimpleNameProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/SimpleNameProvider.java) simply returns the plain value, while the [DefaultDeclarativeQualifiedNameProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/DefaultDeclarativeQualifiedNameProvider.java) concatenates the simple name with the qualified name of its parent exported [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java). This effectively simulates the qualified name computation of most namespace-based languages (like e.g. Java).

As already mentioned, the default implementation strategy exports every model element that the IQualifiedNameProvider can provide a name for. This is a good starting point, but when your models become bigger and you have a lot of them the index will become larger and larger. In most scenarios only a small part of your model should be visible from outside. For that reason only a small part of your model needs to be in the index. If you come to that point, please bind a custom implementation of [IDefaultResourceDescriptionStrategy]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IDefaultResourceDescriptionStrategy.java) and create index representations only for those elements that you want to reference to from outside the resource they are contained in. From within the resource, references to those filtered elements are still possible as long as they have a name. In summary, there are two ways to control which elements will go into the index. The first one is through the [IQualifiedNameProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/IQualifiedNameProvider.java), but this implies that an element is not referable even within the same resource. The second one is though the [IDefaultResourceDescriptionStrategy]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IDefaultResourceDescriptionStrategy.java), which does not imply that you cannot refer to the elment within the same resource.

Beside the exported elements the index contains [IReferenceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IReferenceDescription.java)s that contain the information who is referencing who. They are created through the Manager and IDefaultResourceDescriptionStrategy, too. If there is a model element that references another model element, the IDefaultResourceDescriptionStrategy creates an IReferenceDescription that contains the URI of the referencing element (sourceEObjectURI) and the referenced element (targetEObjectURI). At the end this IReferenceDescriptions are very useful to find references and calculate affected resources.

As mentioned above, in order to calculate an [IResourceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) for a resource the framework asks the [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) which delegates to the IDefaultResourceDescriptionStrategy. To convert between a [QualifiedName]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/QualifiedName.java) and its [String]({{site.javadoc.java}}/java/lang/String.html) representation you can use the [IQualifiedNameConverter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/IQualifiedNameConverter.java). Here is some Java code showing how to do that:

```java
@Inject IQualifiedNameConverter converter;

Manager manager = // obtain an instance of IResourceDescription.Manager
IResourceDescription description = manager.getResourceDescription(resource);
for (IEObjectDescription eod : description.getExportedObjects()) {
  System.out.println(converter.toString(eod.getQualifiedName()));
}
```

In order to obtain a [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) it is best to ask the corresponding [IResourceServiceProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceServiceProvider.java). That is because each language might have a totally different implementation, and as you might refer from your language to a different language you cannot reuse your language's [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java). One basically asks the [Registry]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceServiceProvider.java) (there is usually one global instance) for an [IResourceServiceProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceServiceProvider.java), which in turn provides a [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) along other useful services.

If you are running in a Guice enabled scenario, the code looks like this:

```java
@Inject
private IResourceServiceProvider.Registry rspr;

private IResourceDescription.Manager getManager(Resource res) {
  IResourceServiceProvider resourceServiceProvider =
    rspr.getResourceServiceProvider(res.getURI());
  return resourceServiceProvider.getResourceDescriptionManager();
}
```

If you don't run in a Guice enabled context you will likely have to directly access the singleton:

```java
private IResourceServiceProvider.Registry rspr =
  IResourceServiceProvider.Registry.INSTANCE;
```

However, we strongly encourage you to use [dependency injection](302_configuration.html#dependency-injection). Now that we know how to export elements to be referable from other resources, we need to learn how those exported [IEObjectDescriptions]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java) can be made available to the referencing resources. That is the responsibility of [global scoping]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IGlobalScopeProvider.java) which is described in the following section.

If you would like to see what's in the index, you could use the 'Open Model Element' dialog from the navigation menu entry.

#### Global Scopes Based On External Configuration (e.g. Class Path Based) {#index-based}

Instead of explicitly referring to imported resources, another option is to have some kind of external configuration in order to define what is visible from outside a resource. Java for instance uses the notion of the class path to define containers (jars and class folders) which contain referenceable elements. In the case of Java the order of such entries is also important.

To enable support for this kind of global scoping in Xtext, a [DefaultGlobalScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/impl/DefaultGlobalScopeProvider.java) has to be bound to the [IGlobalScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IGlobalScopeProvider.java) interface. By default Xtext leverages the class path mechanism since it is well designed and already understood by most of our users. The available tooling provided by JDT and PDE to configure the class path adds even more value. However, it is just a default: you can reuse the infrastructure without using Java and be independent from the JDT.

In order to know what is available in the "world", a global scope provider which relies on external configuration needs to read that configuration in and be able to find all candidates for a certain [EReference]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EReference.java). If you don't want to force users to have a folder and file name structure reflecting the actual qualified names of the referenceable [EObjects]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java), you'll have to load all resources up front and either keep holding them in memory or remember all information which is needed for the resolution of cross-references. In Xtext that information is provided by a so called [IEObjectDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java).

##### About the Index, Containers and Their Manager {#containers}

Xtext ships with an index which remembers all [IResourceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) and their [IEObjectDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java) objects. In the IDE-context (i.e. when running the editor, etc.) the index is updated by an incremental project builder. As opposed to that, in a non-UI context you typically do not have to deal with changes, hence the infrastructure can be much simpler. In both situations the global index state is held by an implementation of [IResourceDescriptions]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescriptions.java) (note the plural form!). The bound singleton in the UI scenario is even aware of unsaved editor changes, such that all linking happens to the latest maybe unsaved version of the resources. You will find the Guice configuration of the global index in the UI scenario in [SharedModule]({{site.src.xtext}}/plugins/org.eclipse.xtext.ui.shared/src/org/eclipse/xtext/ui/shared/internal/SharedModule.java).

The index is basically a flat list of instances of [IResourceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java). The index itself doesn't know about visibility constraints due to class path restriction. Rather than that, they are defined by the referencing language by means of so called [IContainers]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java): While Java might load a resource via [ClassLoader.loadResource()]({{site.javadoc.java}}/java/lang/ClassLoader.html) (i.e. using the class path mechanism), another language could load the same resource using the file system paths.

Consequently, the information which container a resource belongs to depends on the referencing context. Therefore an [IResourceServiceProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceServiceProvider.java) provides another interesting service, which is called [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java). For a given [IResourceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java), the [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java) provides you with the [IContainer]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java) as well as with a list of all [IContainers]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java) which are visible from there. Note that the [index]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescriptions.java) is globally shared between all languages while the [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java) which adds the semantics of containers, can be very different depending on the language. The following method lists all resources visible from a given [Resource]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/resource/Resource.java):

```java
@Inject
IContainer.Manager manager;

public void listVisibleResources(
        Resource myResource, IResourceDescriptions index) {
  IResourceDescription descr =
        index.getResourceDescription(myResource.getURI());
  for(IContainer visibleContainer:
        manager.getVisibleContainers(descr, index)) {
    for(IResourceDescription visibleResourceDesc:
            visibleContainer.getResourceDescriptions()) {
      System.out.println(visibleResourceDesc.getURI());
    }
  }
}
```

Xtext ships two implementations of [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java) which are usually bound with Guice: The default binding is to [SimpleResourceDescriptionsBasedContainerManager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/impl/SimpleResourceDescriptionsBasedContainerManager.java), which assumes all [IResourceDescription]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IResourceDescription.java) to be in a single common container. If you don't care about container support, you'll be fine with this one. Alternatively, you can bind [StateBasedContainerManager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/containers/StateBasedContainerManager.java) and an additional [IAllContainersState]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/containers/IAllContainersState.java) which keeps track of the set of available containers and their visibility relationships.

Xtext offers a couple of strategies for managing containers: If you're running an Eclipse workbench, you can define containers based on Java projects and their class paths or based on plain Eclipse projects. Outside Eclipse, you can provide a set of file system paths to be scanned for models. All of these only differ in the bound instance of [IAllContainersState]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/containers/IAllContainersState.java) of the referring language. These will be described in detail in the following sections.

![IContainer Management](images/index_container.png)

##### JDT-Based Container Manager {#jdt-based-containers}

As JDT is an Eclipse feature, this JDT-based container management is only available in the UI scenario. It assumes so called [IPackageFragmentRoots]({{site.javadoc.eclipse-jdt}}/org/eclipse/jdt/core/IPackageFragmentRoot.html) as containers. An [IPackageFragmentRoot]({{site.javadoc.eclipse-jdt}}/org/eclipse/jdt/core/IPackageFragmentRoot.html) in JDT is the root of a tree of Java model elements. It usually refers to

*   a source folder of a Java project,
*   a referenced jar,
*   a class path entry of a referenced Java project, or
*   the exported packages of a required PDE plug-in.

So for an element to be referable, its resource must be on the class path of the caller's Java project and it must be exported (as described above).

As this strategy allows to reuse a lot of nice Java things like jars, OSGi, maven, etc. it is part of the default: You should not have to reconfigure anything to make it work. Nevertheless, if you messed something up, make sure you bind

```java
public Class<? extends IContainer.Manager> bindIContainer$Manager() {
  return StateBasedContainerManager.class;
}
```

in the runtime module and

```java
public Provider<IAllContainersState> provideIAllContainersState() {
  return org.eclipse.xtext.ui.shared.Access.getJavaProjectsState();
}
```

in the UI module of the referencing language. The latter looks a bit more difficult than a common binding, as we have to bind a global singleton to a Guice provider. A [StrictJavaProjectsState]({{site.src.xtext}}/plugins/org.eclipse.xtext.ui/src/org/eclipse/xtext/ui/containers/StrictJavaProjectsState.java) requires all elements to be on the class path, while the default [JavaProjectsState]({{site.src.xtext}}/plugins/org.eclipse.xtext.ui/src/org/eclipse/xtext/ui/containers/JavaProjectsState.java) also allows models in non-source folders.

##### Eclipse Project-Based Containers {#project-based-containers}

If the class path based mechanism doesn't work for your case, Xtext offers an alternative container manager based on plain Eclipse projects: Each project acts as a container and the project references (*Properties &rarr; Project References*) are the visible containers.

In this case, your runtime module should define

```java
public Class<? extends IContainer.Manager> bindIContainer$Manager() {
  return StateBasedContainerManager.class;
}
```

and the UI module should bind

```java
public Provider<IAllContainersState> provideIAllContainersState() {
  return org.eclipse.xtext.ui.shared.Access.getWorkspaceProjectsState();
}
```

##### ResourceSet-Based Containers {#resource-set-containers}

If you need a [Manager]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java) that is independent of Eclipse projects, you can use the [ResourceSetBasedAllContainersState]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/containers/ResourceSetBasedAllContainersState.java). This one can be configured with a mapping of container handles to resource URIs.

It is unlikely you want to use this strategy directly in your own code, but it is used in the back-end of the MWE2 workflow component [Reader]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/mwe/Reader.java). This is responsible for reading in models in a workflow, e.g. for later code generation. The [Reader]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/mwe/Reader.java) allows to either scan the whole class path or a set of paths for all models therein. When paths are given, each path entry becomes an [IContainer]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IContainer.java) of its own.

```java
component = org.eclipse.xtext.mwe.Reader {
  // lookup all resources on the class path
  // useJavaClassPath = true

  // or define search scope explicitly
  path = "src/models"
  path = "src/further-models"

  ...
}
```

### Local Scoping {#local-scoping}

We now know how the outer world of referenceable elements can be defined in Xtext. Nevertheless, not everything is available in all contexts and with a global name. Rather than that, each context can usually have a different scope. As already stated, scopes can be nested, i.e. a scope can contain elements of a parent scope in addition to its own elements. When parent and child scope contain different elements with the same name, the parent scope's element will usually be *shadowed* by the element from the child scope.

To illustrate that, let's have a look at Java: Java defines multiple kinds of scopes (object scope, type scope, etc.). For Java one would create the scope hierarchy as commented in the following example:

```java
// file contents scope
import static my.Constants.STATIC;

public class ScopeExample { // class body scope
  private Object field = STATIC;

  private void method(String param) { // method body scope
    String localVar = "bar";
    innerBlock: { // block scope
      String innerScopeVar = "foo";
      Object field = innerScopeVar;
      // the scope hierarchy at this point would look like this:
      //  blockScope{field,innerScopeVar}->
      //  methodScope{localVar, param}->
      //  classScope{field}-> ('field' is shadowed)
      //  fileScope{STATIC}->
      //  classpathScope{
      //      'all qualified names of accessible static fields'} ->
      //  NULLSCOPE{}
      //
    }
    field.add(localVar);
  }
}
```

In fact the class path scope should also reflect the order of class path entries. For instance:

```java
classpathScope{stuff from bin/}
-> classpathScope{stuff from foo.jar/}
-> ...
-> classpathScope{stuff from JRE System Library}
-> NULLSCOPE{}
```

Please find the motivation behind this and some additional details in [this blog post](http://blog.efftinge.de/2009/01/xtext-scopes-and-emf-index.html) .

### Imported Namespace Aware Scoping {#namespace-imports}

The imported namespace aware scoping is based on qualified names and namespaces. It adds namespace support to your language, which is comparable and similar to namespaces in Scala and C#. Scala and C# both allow to have multiple nested packages within one file, and you can put imports per namespace, such that imported names are only visible within that namespace. See the domain model example: its scope provider extends [ImportedNamespaceAwareLocalScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/impl/ImportedNamespaceAwareLocalScopeProvider.java).

#### [IQualifiedNameProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/IQualifiedNameProvider.java)

The [ImportedNamespaceAwareLocalScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/impl/ImportedNamespaceAwareLocalScopeProvider.java) makes use of the so called [IQualifiedNameProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/IQualifiedNameProvider.java) service. It computes [QualifiedNames]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/QualifiedName.java) for [EObjects]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java). A qualified name consists of several segments. The [default implementation]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/naming/DefaultDeclarativeQualifiedNameProvider.java) uses a simple name look-up composing the qualified name of the simple names of all containers and the object itself. It also allows to override the name computation declaratively. The following snippet shows how you could make *Transitions* in the state machine example referable by giving them a name. Don't forget to bind your implementation in your runtime module.

```java
FowlerDslQualifiedNameProvider
      extends DefaultDeclarativeQualifiedNameProvider {
  public QualifiedName qualifiedName(Transition t) {
    if(t.getEvent() == null || !(t.eContainer() instanceof State))
      return null;
    else
      return QualifiedName.create((State)t.eContainer()).getName(),
        t.getEvent().getName());
  }
}
```

#### Importing Namespaces

The [ImportedNamespaceAwareLocalScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/impl/ImportedNamespaceAwareLocalScopeProvider.java) looks up [EAttributes]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EAttribute.java) with name '*importedNamespace*' and interprets them as import statements.

```xtext
Root:
    imports+=Import*
    childs+=Child*;

Import:
    'import' importedNamespace=QualifiedName ('.*')?;

QualifiedName:
  ID ('.' ID)*;
```

By default qualified names with or without a wildcard at the end are supported. For an import of a qualified name the simple name is made available as we know from e.g. Java, where `import java.util.Set;` makes it possible to refer to [java.util.Set]({{site.javadoc.java}}/java/util/Set.html) by its simple name [Set]({{site.javadoc.java}}/java/util/Set.html). Contrary to Java, the import is not active for the whole file, but only for the namespace it is declared in and its child namespaces. That is why you can write the following in the example DSL:

```domainexample
package foo {
  import bar.Foo
  entity Bar extends Foo {
  }
}

package bar {
  entity Foo {}
}
```

Of course the declared elements within a package are as well referable by their simple name:

```domainexample
package bar {
  entity Bar extends Foo {}
  entity Foo {}
}
```

The following would also be ok:

```domainexample
package bar {
  entity Bar extends bar.Foo {}
  entity Foo {}
}
```

See the [JavaDocs]({{site.javadoc.xtext}}/org/eclipse/xtext/scoping/impl/package-summary.html) and [this blog post](https://blogs.itemis.de/leipzig/archives/773) for details.

## Value Converter {#value-converter}

Value converters are registered to convert the parsed text into a data type instance and vice versa. The primary hook is the [IValueConverterService]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/IValueConverterService.java) and the concrete implementation can be registered via the runtime [Guice module](302_configuration.html#guicemodules). Simply override the corresponding binding in your runtime module like shown in this example:

```java
@Override
public Class<? extends IValueConverterService>
    bindIValueConverterService() {
    return MySpecialValueConverterService.class;
}
```

The most simple way to register additional value converters is to make use of [AbstractDeclarativeValueConverterService]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/impl/AbstractDeclarativeValueConverterService.java), which allows to declaratively register an [IValueConverter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/IValueConverter.java) by means of an annotated method.

```java
@ValueConverter(rule = "MyRuleName")
public IValueConverter<MyDataType> getMyRuleNameConverter() {
  return new MyValueConverterImplementation();
}
```

If you use the common terminals grammar `org.eclipse.xtext.common.Terminals` you should extend the [DefaultTerminalConverters]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/common/services/DefaultTerminalConverters.java) and override or add value converters by adding the respective methods. In addition to the explicitly defined converters in the default implementation, a delegating converter is registered for each available [EDataType]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EDataType.java). The delegating converter reuses the functionality of the corresponding EMF [EFactory]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EFactory.java).

Many languages introduce a concept for qualified names, i.e. names composed of namespaces separated by a delimiter. Since this is such a common use case, Xtext provides an extensible converter implementation for qualified names. The [QualifiedNameValueConverter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/impl/QualifiedNameValueConverter.java) handles comments and white space gracefully and is capable to use the appropriate value converter for each segment of a qualified name. This allows for individually quoted segments. The domainmodel example shows how to use it.

The protocol of an [IValueConverter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/IValueConverter.java) allows to throw a [ValueConverterException]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/ValueConverterException.java) if something went wrong. The exception is propagated as a syntax error by the parser or as a validation problem by the [ConcreteSyntaxValidator]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/validation/impl/ConcreteSyntaxValidator.java) if the value cannot be converted to a valid string. The [AbstractLexerBasedConverter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/impl/AbstractLexerBasedConverter.java) is useful when implementing a custom value converter. If the converter needs to know about the rule that it currently works with, it may implement the interface [RuleSpecific]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/IValueConverter.java). The framework will set the rule such as the implementation may use it afterwards.

## Serialization {#serialization}

Serialization is the process of transforming an EMF model into its textual representation. Thereby, serialization complements parsing and lexing.

In Xtext, the process of serialization is split into the following steps:

1.  Validating the semantic model. This is optional, enabled by default, done by the [concrete syntax validator](#concrete-syntax-validation) and can be turned off in the [save options](#save-options).
1.  Matching the model elements with the grammar rules and creating a stream of tokens. This is done by the [parse tree constructor](#parse-tree-constructor).
1.  Associating comments with semantic objects. This is done by the [comment associator](#comment-associater).
1.  Associating existing nodes from the node model with tokens from the token stream.
1.  [Merging existing white space](#hidden-token-merger) and line-wraps into the token stream.
1.  Adding further needed white space or replacing all white space using a [formatter](#formatting).

Serialization is invoked when calling [XtextResource.save(..)]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/XtextResource.java). Furthermore, the [Serializer]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/Serializer.java) provides resource-independent support for serialization. Another situation that triggers serialization is applying [quick fixes](304_ide_concepts.html#quick-fixes) with semantic modifications. Serialization is *not* called when a textual editors contents is saved to disk.

### The Contract {#serialization-contract}

The contract of serialization says that a model which is saved (serialized) to its textual representation and then loaded (parsed) again yields a new model that is equal to the original model. Please be aware that this does *not* imply that loading a textual representation and serializing it back produces identical textual representations. However, the serialization algorithm tries to restore as much information as possible. That is, if the parsed model was not modified in-memory, the serialized output will usually be equal to the previous input. Unfortunately, this cannot be ensured for each and every case. A use case where is is hardly possible, is shown in the following example:

```xtext
MyRule:
  (xval+=ID | yval+=INT)*;
```

The given *MyRule* reads *ID*- and *INT*-elements which may occur in an arbitrary order in the textual representation. However, when serializing the model all *ID*-elements will be written first and then all *INT*-elements. If the order is important it can be preserved by storing all elements in the same list - which may require wrapping the *ID*- and *INT*-elements into other objects.

### Roles of the Semantic Model and the Node Model During Serialization

A serialized document represents the state of the semantic model. However, if there is a node model available (i.e. the semantic model has been created by the parser), the serializer

*   preserves [existing white spaces](#hidden-token-merger) from the node model.
*   preserves [existing comments](#comment-associater) from the node model.
*   preserves the representation of cross-references: If a cross-referenced object can be identified by multiple names (i.e. scoping returns multiple [IEObjectDescriptions]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IEObjectDescription.java) for the same object), the serializer tries to keep the name that was used in the input file.
*   preserves the representation of values: For values handled by the [value converter](#value-converter), the serializer checks whether the textual representation converted to a value equals the value from the semantic model. If that is true, the textual representation is kept.

### Parse Tree Constructor {#parse-tree-constructor}

The parse tree constructor usually does not need to be customized since it is automatically derived from the [Xtext Grammar](301_grammarlanguage.html). However, it can be helpful to look into it to understand its error messages and its runtime performance.

For serialization to succeed, the parse tree constructor must be able to *consume* every non-transient element of the to-be-serialized EMF model. To *consume* means, in this context, to write the element to the textual representation of the model. This can turn out to be a not-so-easy-to-fulfill requirement, since a grammar usually introduces implicit constraints to the EMF model as explained for the [concrete syntax validator](#concrete-syntax-validation).

If a model can not be serialized, an [XtextSerializationException]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/XtextSerializationException.java) is thrown. Possible reasons are listed below:

*   A model element can not be consumed. This can have the following reasons/solutions:
    *   The model element should not be stored in the model.
    *   The grammar needs an assignment which would consume the model element.
    *   The [transient value service](#transient-values) can be used to indicate that this model element should not be consumed.
*   An assignment in the grammar has no corresponding model element. The default transient value service considers a model element to be transient if it is *unset* or *equals* its default value. However, the parse tree constructor may serialize default values if this is required by a grammar constraint to be able to serialize another model element. The following solution may help to solve such a scenario:
    *   A model element should be added to the model.
    *   The assignment in the grammar should be made optional.
*   The type of the model element differs from the type in the grammar. The type of the model element must be identical to the return type of the grammar rule or the action's type. Subtypes are not allowed.
*   [Value conversion](#value-converter) fails. The value converter can indicate that a value is not serializable by throwing a [ValueConverterException]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/conversion/ValueConverterException.java).
*   An enum literal is not allowed at this position. This can happen if the referenced enum rule only lists a subset of the literals of the actual enumeration.

To understand error messages and performance issues of the parse tree constructor it is important to know that it implements a backtracking algorithm. This basically means that the grammar is used to specify the structure of a tree in which one path (from the root node to a leaf node) is a valid serialization of a specific model. The parse tree constructor's task is to find this path - with the condition that all model elements are consumed while walking this path. The parse tree constructor's strategy is to take the most promising branch first (the one that would consume the most model elements). If the branch leads to a dead end (for example, if a model element needs to be consumed that is not present in the model), the parse tree constructor goes back the path until a different branch can be taken. This behavior has two consequences:

*   In case of an error, the parse tree constructor has found only dead ends but no leaf. It cannot tell which dead end is actually erroneous. Therefore, the error message lists dead ends of the longest paths, a fragment of their serialization and the reason why the path could not be continued at this point. The developer has to judge on his own which reason is the actual error.
*   For reasons of performance, it is critical that the parse tree constructor takes the most promising branch first and detects wrong branches early. One way to achieve this is to avoid having many rules which return the same type and which are called from within the same alternative in the grammar.

### Options {#save-options}

[SaveOptions]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/SaveOptions.java) can be passed to [XtextResource.save(options)]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/XtextResource.java) and to [Serializer.serialize(..)]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/Serializer.java). Available options are:

*   **Formatting.** Default: `false`. If enabled, it is the [formatters](#formatting) job to determine all white space information during serialization. If disabled, the formatter only defines white space information for the places in which no white space information can be preserved from the node model. E.g. When new model elements are inserted or there is no node model.
*   **Validating.** Default: `true`: Run the [concrete syntax validator](#concrete-syntax-validation) before serializing the model.

### Preserving Comments from the Node Model {#comment-associater}

The [ICommentAssociater]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/ICommentAssociater.java) associates comments with semantic objects. This is important in case an element in the semantic model is moved to a different position and the model is serialized, one expects the comments to be moved to the new position in the document as well.

Which comment belongs to which semantic object is surely a very subjective issue. The [default implementation]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/impl/DefaultCommentAssociater.java) behaves as follows, but can be customized:

*   If there is a semantic token before a comment and in the same line, the comment is associated with this token's semantic object.
*   In all other cases, the comment is associated with the semantic object of the next following object.

### Transient Values {#transient-values}

Transient values are values or model elements which are not persisted (written to the textual representation in the serialization phase). If a model contains model elements which can not be serialized with the current grammar, it is critical to mark them transient using the [ITransientValueService]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/ITransientValueService.java), or serialization will fail. The default implementation marks all model elements transient, which are `eStructuralFeature.isTransient()` or not `eObject.eIsSet(eStructuralFeature)`. By default, EMF returns `false` for `eIsSet(..)` if the value equals the default value.

### Unassigned Text {#unassigned-text}

If there are calls of data type rules or terminal rules that do not reside in an assignment, the serializer by default doesn't know which value to use for serialization.

Example:

```xtext
PluralRule:
  'contents:' count=INT Plural;

terminal Plural:
  'item' | 'items';
```

Valid models for this example are `contents 1 item` or `contents 5 items. However, it is not stored in the semantic model whether the keyword *item* or *items* has been parsed. This is due to the fact that the rule call *Plural* is unassigned. However, the [parse tree constructor](#parse-tree-constructor) needs to decide which value to write during serialization. This decision can be be made by customizing the [IValueSerializer.serializeUnassignedValue(EObject, RuleCall, INode)]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/ITokenSerializer.java).

### Cross-Reference Serializer {#cross-reference-serializer}

The cross-reference serializer specifies which values are to be written to the textual representation for cross-references. This behavior can be customized by implementing [ICrossReferenceSerializer]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/ITokenSerializer.java). The default implementation delegates to various other services such as the [IScopeProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/scoping/IScopeProvider.java) or the [LinkingHelper]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/linking/impl/LinkingHelper.java) each of which may be the better place for customization.

### Merge White Space {#hidden-token-merger}

After the [parse tree constructor](#parse-tree-constructor) has done its job to create a stream of tokens which are to be written to the textual representation, and the [comment associator](#comment-associater) has done its work, existing white space form the node model is merged into the stream.

The strategy is as follows: If two tokens follow each other in the stream and the corresponding nodes in the node model follow each other as well, then the white space information in between is kept. In all other cases it is up to the [formatter](#formatting) to calculate new white space information.

### Token Stream {#token-stream}

The [parse tree constructor](#parse-tree-constructor) and the [formatter](#formatting) use an [ITokenStream]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/ITokenStream.java) for their output, and the latter for its input as well. This allows for chaining the two components. Token streams can be converted to a [String]({{site.javadoc.java}}/java/lang/String.html) using the [TokenStringBuffer]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/impl/TokenStringBuffer.java) and to a [Writer]({{site.javadoc.java}}/java/io/Writer.html) using the [WriterTokenStream]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parsetree/reconstr/impl/WriterTokenStream.java).

```java
public interface ITokenStream {

  void flush() throws IOException;
  void writeHidden(EObject grammarElement, String value);
  void writeSemantic(EObject grammarElement, String value);
}
```

## Formatting (Pretty Printing) {#formatting}

Formatting is the process of rearranging the text in a document to improve the readability without changing the semantic value of the document. Therefore, a formatter is responsible for arranging line-wraps, indentation, whitespace, etc. in a text to emphasize its structure. A formatter is not supposed to alter a document in a way that impacts the semantic model.

### New API in Xtext 2.8 {#formatting-new}

The new formatting API is available since Xtext 2.8. It resolves the limitations of the first API which was present since the first version of Xtext. The new API allows to implement formatting not only based on the static structure of the grammar, but it is possible to make decisions based on the actual model structure. Things that are now possible include:

*   Add line breaks to long lists of items but keep short lists in one line
*   Arrange elements in a tabular layout
*   Apply formatting to values of data type rules or comments
*   Consider the existing whitespace information and implement an adaptive layout
*   Provide user configurable preferences for the formatting settings

The actual formatting is done by constructing a list of text replacements. A text replacement describes a new text which should replace an existing part of the document. This is described by offset and length. Applying the text replacements turns the unformatted document into a formatted document.

To invoke the formatter programmatically, you need to instantiate a [request]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/FormatterRequest.java) and pass it to the [formatter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/IFormatter2.java). The formatter will return a list of text replacements. The document modification itself can be performed by a [utility]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/TextReplacements.java) that is part of the formatting API.

Implementors of a formatter should extend [AbstractFormatter2]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/AbstractFormatter2.java) and add dispatch method for the model elements that should be formatted. The format routine has to be invoked recursively if the children of an object should be formatted, too.

The following example illustrates that pattern. An instance of package declaration is passed to the format method along with the current [formattable document]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/IFormattableDocument.java). In this scenario, the package name is surrounded by a single space, the curly brace is followed by a new line and increased indentation etc. All elements within that package should be formatted, too, thus `format(..)` is invoked on these as well.

```xtend
def dispatch void format(PackageDeclaration p, extension IFormattableDocument doc) {
  p.regionForFeature(ABSTRACT_ELEMENT__NAME).surround[oneSpace]
  p.regionForKeyword("{").append[newLine; increaseIndentation]
  for (AbstractElement element : p.elements) {
    format(element, doc);
    element.append[setNewLines(1, 1, 2)]
  }
  p.regionForKeyword("}").prepend[decreaseIndentation]
}
```

The API is designed in a way that allows to describe the formatting in a declarative way by calling methods on the [IHiddenRegionFormatter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/IHiddenRegionFormatter.java) which is made available inside invocations of `prepend`, `surround` or `append` to specify the formatting rules. This can be done in arbitrary order &ndash; the infrastructure will reorder all the configurations to execute them from top to bottom of the document. If the configuration-based approach is not sufficient for a particular use case, the [document]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/IFormattableDocument.java) also accepts imperative logic that is associated with a given range. The [ITextReplacer]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting2/ITextReplacer.java) that can be added directly to the document allows to perform all kinds of modifications to the text in the region that it is associated with.

More detailed information about the API is available as [JavaDoc on the org.eclipse.xtext.formatting2 package]({{site.javadoc.xtext}}/org/eclipse/xtext/formatting2/package-summary.html).

### Before Xtext 2.8 {#formatting-old}

**The API in `org.eclipse.xtext.formatting` is available since the early days of Xtext and still present in Xtext 2.8. However, it will be deprecated and eventually be removed because of the limitations that it imposes due to its declarative and static approach. New formatting implementations should be based on the new API in `org.eclipse.xtext.formatting2`.**

A formatter can be implemented via the [IFormatter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/IFormatter.java) service. Technically speaking, a formatter is a [Token Stream](#token-stream) which inserts/removes/modifies hidden tokens (white space, line-breaks, comments).

The formatter is invoked during the [serialization phase](#serialization) and when the user triggers formatting in the editor (for example, using the CTRL+SHIFT+F shortcut).

Xtext ships with two formatters:

*   The [OneWhitespaceFormatter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/impl/OneWhitespaceFormatter.java) simply writes one white space between all tokens.
*   The [AbstractDeclarativeFormatter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/impl/AbstractDeclarativeFormatter.java) allows advanced configuration using a [FormattingConfig]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/impl/FormattingConfig.java). Both are explained below.

A declarative formatter can be implemented by subclassing [AbstractDeclarativeFormatter]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/impl/AbstractDeclarativeFormatter.java), as shown in the following example:

```java
public class ExampleFormatter extends AbstractDeclarativeFormatter {

  @Override
  protected void configureFormatting(FormattingConfig c) {
    ExampleLanguageGrammarAccess f = getGrammarAccess();

    c.setAutoLinewrap(120);

    // find common keywords an specify formatting for them
    for (Pair<Keyword, Keyword> pair : f.findKeywordPairs("(", ")")) {
      c.setNoSpace().after(pair.getFirst());
      c.setNoSpace().before(pair.getSecond());
    }
    for (Keyword comma : f.findKeywords(",")) {
      c.setNoSpace().before(comma);
    }

    // formatting for grammar rule Line
    c.setLinewrap(2).after(f.getLineAccess().getSemicolonKeyword_1());
    c.setNoSpace().before(f.getLineAccess().getSemicolonKeyword_1());

    // formatting for grammar rule TestIndentation
    c.setIndentationIncrement().after(
        f.getTestIndentationAccess().getLeftCurlyBracketKeyword_1());
    c.setIndentationDecrement().before(
        f.getTestIndentationAccess().getRightCurlyBracketKeyword_3());
    c.setLinewrap().after(
        f.getTestIndentationAccess().getLeftCurlyBracketKeyword_1());
    c.setLinewrap().after(
        f.getTestIndentationAccess().getRightCurlyBracketKeyword_3());

    // formatting for grammar rule Param
    c.setNoLinewrap().around(f.getParamAccess().getColonKeyword_1());
    c.setNoSpace().around(f.getParamAccess().getColonKeyword_1());

    // formatting for Comments
    cfg.setLinewrap(0, 1, 2).before(g.getSL_COMMENTRule());
    cfg.setLinewrap(0, 1, 2).before(g.getML_COMMENTRule());
    cfg.setLinewrap(0, 1, 1).after(g.getML_COMMENTRule());
  }
}
```

The formatter has to implement the method `configureFormatting(...)` which declaratively sets up a [FormattingConfig]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/impl/FormattingConfig.java).

The [FormattingConfig]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/impl/FormattingConfig.java) consist of general settings and a set of formatting instructions:

#### General FormattingConfig Settings

`setAutoLinewrap(int)` defines the amount of characters after which a line-break should be dynamically inserted between two tokens. The instructions `setNoLinewrap().???()`, `setNoSpace().???()` and `setSpace(space).???()` suppress this behavior locally. The default is 80.

#### FormattingConfig Instructions

Per default, the declarative formatter inserts one white space between two tokens. Instructions can be used to specify a different behavior. They consist of two parts: *When* to apply the instruction and *what* to do.

To understand *when* an instruction is applied think of a stream of tokens whereas each token is associated with the corresponding grammar element. The instructions are matched against these grammar elements. The following matching criteria exist:

*   `after(element)`: The instruction is applied after the grammar element has been matched. For example, if your grammar uses the keyword `";"` to end lines, this can instruct the formatter to insert a line break after the semicolon.
*   `before(element)`: The instruction is executed before the matched element. For example, if your grammar contains lists which separate their values with the keyword `","`, you can instruct the formatter to suppress the white space before the comma.
*   `around(element)`: This is the same as `before(element)` combined with `after(element)`.
*   `between(left, right)`: This matches if *left* directly follows *right* in the document. There may be no other tokens in between *left* and *right*.
*   `bounds(left, right)`: This is the same as `after(left)` combined with `before(right)`.
*   `range(start, end)`: The rule is enabled when *start* is matched, and disabled when *end* is matched. Thereby, the rule is active for the complete region which is surrounded by *start* and *end*.

The term *tokens* is used slightly different here compared to the parser/lexer. Here, a token is a keyword or the string that is matched by a terminal rule, data type rule or cross-reference. In the terminology of the lexer a data type rule can match a composition of multiple tokens.

The parameter *element* can be a grammar's [AbstractElement]({{site.src.xtext}}/plugins/org.eclipse.xtext/emf-gen/org/eclipse/xtext/AbstractElement.java) or a grammar's [AbstractRule]({{site.src.xtext}}/plugins/org.eclipse.xtext/emf-gen/org/eclipse/xtext/AbstractRule.java). All grammar rules and almost all abstract elements can be matched. This includes rule calls, parser rules, groups and alternatives. The semantic of `before(element)`, `after(element)`, etc. for rule calls and parser rules is identical to when the parser would "pass" this part of the grammar. The stack of called rules is taken into account. The following abstract elements can *not* have assigned formatting instructions:

*   Actions. E.g. `{MyAction}` or `{MyAction.myFeature=current}`.
*   Grammar elements nested in data type rules. This is due to to the fact that tokens matched by a data type rule are treated as atomic by the serializer. To format these tokens, please implement a [ValueConverter](#value-converter).
*   Grammar elements nested in [CrossReference]({{site.src.xtext}}/plugins/org.eclipse.xtext/emf-gen/org/eclipse/xtext/CrossReference.java).

After having explained how rules can be activated, this is what they can do:

*   `setIndentationIncrement()` increments indentation by one unit at this position. Whether one unit consists of one tab-character or spaces is defined by [IIndentationInformation]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/formatting/IIndentationInformation.java). The default implementation consults Eclipse's [IPreferenceStore]({{site.javadoc.eclipse-platform}}/org/eclipse/jface/preference/IPreferenceStore.html).
*   `setIndentationDecrement()` decrements indentation by one unit.
*   `setLinewrap()`: Inserts a line-wrap at this position.
*   `setLinewrap(int count)`: Inserts *count* numbers of line-wrap at this position.
*   `setLinewrap(int min, int def, int max)`: If the amount of line-wraps that have been at this position before formatting can be determined (i.e. when a node model is present), then the amount of of line-wraps is adjusted to be within the interval *min*, *max* and is then reused. In all other cases *def* line-wraps are inserted. Example: `setLinewrap(0, 0, 1)` will preserve existing line-wraps, but won't allow more than one line-wrap between two tokens.
*   `setNoLinewrap()`: Suppresses automatic line wrap, which may occur when the line's length exceeds the defined limit.
*   `setSpace(String space)`: Inserts the string *space* at this position. If you use this to insert something else than white space, tabs or newlines, a small puppy will die somewhere in this world.
*   `setNoSpace()`: Suppresses the white space between tokens at this position. Be aware that between some tokens a white space is required to maintain a valid concrete syntax.

#### Grammar Element Finders

Sometimes, if a grammar contains many similar elements for which the same formatting instructions ought to apply, it can be tedious to specify them for each grammar element individually. The [IGrammarAccess]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/IGrammarAccess.java) provides convenience methods for this. The find methods are available for the grammar and for each parser rule.

*   `findKeywords(String... keywords)` returns all keywords that equal one of the parameters.
*   `findKeywordPairs(String leftKw, String rightKw)`: returns tuples of keywords from the same grammar rule. Pairs are matched nested and sequentially. Example: for `Rule: '(' name=ID ('(' foo=ID ')') ')' | '(' bar=ID ')'``findKeywordPairs("(", ")")` returns three pairs.

## Fragment Provider (Referencing Xtext Models From Other EMF Artifacts) {#fragment-provider}

Although inter-Xtext linking is not done by URIs, you may want to be able to reference your [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) from non-Xtext models. In those cases URIs are used, which are made up of a part identifying the resource and a second part that points to an object. Each [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) contained in a resource can be identified by a so called *fragment*.

A fragment is a part of an EMF URI and needs to be unique per resource.

The generic resource shipped with EMF provides a generic path-like computation of fragments. These fragment paths are unique by default and do not have to be serialized. On the other hand, they can be easily broken by reordering the elements in a resource.

With an XMI or other binary-like serialization it is also common and possible to use UUIDs. UUIDs are usually binary and technical, so you don't want to deal with them in human readable representations.

However with a textual concrete syntax we want to be able to compute fragments out of the human readable information. We don't want to force people to use UUIDs (i.e. synthetic identifiers) or fragile, relative, generic paths in order to refer to [EObjects]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java).

Therefore one can contribute an [IFragmentProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IFragmentProvider.java) per language. It has two methods: `getFragment(EObject, Fallback)` to calculate the fragment of an [EObject]({{site.src.emf}}/plugins/org.eclipse.emf.ecore/src/org/eclipse/emf/ecore/EObject.java) and `getEObject(Resource, String, Fallback)` to go the opposite direction. The [Fallback]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/IFragmentProvider.java) interface allows to delegate to the default strategy - which usually uses the fragment paths described above.

The following snippet shows how to use qualified names as fragments:

```java
public QualifiedNameFragmentProvider implements IFragmentProvider {

  @Inject
  private IQualifiedNameProvider qualifiedNameProvider;

  public String getFragment(EObject obj, Fallback fallback) {
    String qName = qualifiedNameProvider.getQualifiedName(obj);
    return qName != null ? qName : fallback.getFragment(obj);
  }

  public EObject getEObject(Resource resource,
                            String fragment,
                            Fallback fallback) {
    if (fragment != null) {
      Iterator<EObject> i = EcoreUtil.getAllContents(resource, false);
      while(i.hasNext()) {
        EObject eObject = i.next();
        String candidateFragment = (eObject.eIsProxy())
            ? ((InternalEObject) eObject).eProxyURI().fragment()
            : getFragment(eObject, fallback);
        if (fragment.equals(candidateFragment))
          return eObject;
      }
    }
    return fallback.getEObject(fragment);
  }
}
```

For performance reasons it is usually a good idea to navigate the resource based on the fragment information instead of traversing it completely. If you know that your fragment is computed from qualified names and your model contains something like *NamedElements*, you should split your fragment into those parts and query the root elements, the children of the best match and so on.

Furthermore it's a good idea to have some kind of conflict resolution strategy to be able to distinguish between equally named elements that actually are different, e.g. properties may have the very same qualified name as entities.

## Encoding in Xtext {#encoding}

Encoding, aka *character set*, describes the way characters are encoded into bytes and vice versa. Famous standard encodings are *UTF-8* or *ISO-8859-1*. The list of available encodings can be determined by calling [Charset.availableCharsets()]({{site.javadoc.java}}/java/nio/charset/Charset.html). There is also a list of encodings and their canonical Java names in the [API docs](http://download.oracle.com/javase/1.5.0/docs/guide/intl/encoding.doc.html).

Unfortunately, each platform and/or spoken language tends to define its own native encoding, e.g. *Cp1258* on Windows in Vietnamese or *MacIceland* on Mac OS X in Icelandic.

In an Eclipse workspace, files, folders, projects can have individual encodings, which are stored in the hidden file *.settings/org.eclipse.core.resources.prefs* in each project. If a resource does not have an explicit encoding, it inherits the one from its parent recursively. Eclipse chooses the native platform encoding as the default for the workspace root. You can change the default workspace encoding in the Eclipse preferences *Preferences &rarr; Workspace &rarr; Default text encoding*. If you develop on different platforms, you should consider choosing an explicit common encoding for your text or code files, especially if you use special characters.

While Eclipse allows to define and inspect the encoding of a file, your file system usually doesn't. Given an arbitrary text file there is no general strategy to tell how it was encoded. If you deploy an Eclipse project as a jar (even a plug-in), any encoding information not stored in the file itself is lost, too. Some languages define the encoding of a file explicitly, as in the first processing instruction of an XML file. Most languages don't. Others imply a fixed encoding or offer enhanced syntax for character literals, e.g. the unicode escape sequences *\uXXXX* in Java.

As Xtext is about textual modeling, it allows to tweak the encoding in various places.

### Encoding at Language Design Time

The plug-ins created by the *New Xtext Project* wizard are by default encoded in the workspace's standard encoding. The same holds for all files that Xtext generates in there. If you want to change that, e.g. because your grammar uses/allows special characters, you should manually set the encoding in the properties of these projects after their creation. Do this before adding special characters to your grammar or at least make sure the grammar reads correctly after the encoding change. To tell the Xtext generator to generate files in the same encoding, set the encoding property in the workflow next to your grammar, e.g.

```mwe2
Generator {
  encoding ="UTF-8"
  ...
```

### Encoding at Language Runtime

As each language could handle the encoding problem differently, Xtext offers a service here. The [IEncodingProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parser/IEncodingProvider.java) has a single method `getEncoding(URI)` to define the encoding of the resource with the given URI. Users can implement their own strategy but keep in mind that this is not intended to be a long running method. If the encoding is stored within the model file itself, it should be extractable in an easy way, like from the first line in an XML file. The default implementation returns the default Java character set in the runtime scenario.

In the UI scenario, when there is a workspace, users will expect the encoding of the model files to be settable the same way as for other files in the workspace. The default implementation of the [IEncodingProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parser/IEncodingProvider.java) in the UI scenario therefore returns the file's workspace encoding for files in the workspace and delegates to the runtime implementation for all other resources, e.g. models in a jar or from a deployed plug-in. Keep in mind that you are going to loose the workspace encoding information as soon as you leave this workspace, e.g. deploy your project.

Unless you want to enforce a uniform encoding for all models of your language, we advise to override the runtime service only. It is bound in the runtime module using the binding annotation [@Runtime]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/service/DispatchingProvider.java):

```java
@Override
public void configureRuntimeEncodingProvider(Binder binder) {
    binder.bind(IEncodingProvider.class)
          .annotatedWith(DispatchingProvider.Runtime.class)
          .to(MyEncodingProvider.class);
}
```

For the uniform encoding, bind the plain [IEncodingProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parser/IEncodingProvider.java) to the same implementation in both modules:

```java
@Override
public Class<? extends IEncodingProvider> bindIEncodingProvider() {
    return MyEncodingProvider.class;
}
```

### Encoding of an XtextResource

An [XtextResource]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/resource/XtextResource.java) uses the [IEncodingProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext/src/org/eclipse/xtext/parser/IEncodingProvider.java) of your language by default. You can override that by passing an option on load and save, e.g.

```java
Map<?,?> options = new HashMap();
options.put(XtextResource.OPTION_ENCODING, "UTF-8");
myXtextResource.load(options);

options.put(XtextResource.OPTION_ENCODING, "ISO-8859-1");
myXtextResource.save(options);
```

### Encoding in New Model Projects

The [SimpleProjectWizardFragment]({{site.src.xtext}}/plugins/org.eclipse.xtext.generator/src/org/eclipse/xtext/ui/generator/projectWizard/SimpleProjectWizardFragment.java) generates a wizard that clients of your language can use to create model projects. This wizard expects its templates to be in the encoding of the Generator that created it (see above). As for every new project wizard, its output will be encoded in the default encoding of the target workspace. If your language enforces a special encoding that ignores the workspace settings, you'll have to make sure that your wizard uses the right encoding by yourself.

### Encoding of Xtext Source Code

The source code of the Xtext framework itself is completely encoded in *ISO 8859-1*, which is necessary to make the Xpand templates work everywhere (they use french quotation markup). That encoding is hard coded into the Xtext generator code. You are likely never going to change that.

## Unit Testing the Language {#testing}

Automated tests are crucial for the maintainability and the quality of a software product. That is why it is strongly recommended to write unit tests for your language, too. The Xtext project wizard creates a test project for that purpose. It simplifies the setup procedure both for the Eclipse agnostic tests and the UI tests for Junit4.

The following is about testing the parser and the linker for the *Domainmodel* language from the tutorial. It leverages Xtend to write the test case.

### Creating a simple test class

First of all, a new Xtend class has to be created. Therefore, choose the src folder of the test plugin, and select *New &rarr; Xtend Class* from the context menu. Provide a meaningful name and enter the package before you hit finish.

The core of the test infrastructure is the [XtextRunner]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/XtextRunner.java) and the language specific [IInjectorProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/IInjectorProvider.java). Both have to be provided by means of class annotations:

```xtend
  import org.eclipse.xtext.junit4.XtextRunner
  import org.example.domainmodel.DomainmodelInjectorProvider

  @InjectWith(DomainmodelInjectorProvider)
  @RunWith(XtextRunner)
  class ParserTest {
  }
```

This configuration will make sure that you can use dependency injection in your test class, and that the global EMF registries are properly populated and cleaned up before respectively after each test.

### Writing a parser test

The class *org.eclipse.xtext.junit4.util.ParseHelper* allows to parse an arbitrary string into an AST model. The AST model itself can be traversed and checked afterwards. A static import of [Assert]({{site.javadoc.junit}}/org/junit/Assert.html) leads to concise and readable test cases.

```xtend
  import org.eclipse.xtext.junit4.util.ParseHelper
  import static org.junit.Assert.*

  ...

  @Inject
  ParseHelper<Domainmodel> parser

  @Test
  def void parseDomainmodel() {
    val model = parser.parse('''
      entity MyEntity {
        parent: MyEntity
      }
    ''')
    val entity = model.elements.head as Entity
    assertSame(entity, entity.features.head.type)
  }
```

### How to write tests that includes multiple different languages

If in addition to the main language your tests require using other languages for references from/to your main language, you'll have to parse and load dependant resources into the same ResourceSet first for cross reference resolution to work.

As your main language's default generated [IInjectorProvider]({{site.src.xtext}}/plugins/org.eclipse.xtext.junit4/src/org/eclipse/xtext/junit4/IInjectorProvider.java) (e.g. DomainmodelInjectorProvider) does not know about any other such dependant languages, they must be initialized explicitly. The recommended pattern for this is to create a new subclass of the generated *MyLanguageInjectorProvider* in your *\*.test* project and make sure the dependenant language is intizialized properly. You can and then use this new injector provider instead of the original one in your test's *@InjectWith*:

```xtend
  class MyLanguageWithDependenciesInjectorProvider extends MyLanguageInjectorProvider {
    override internalCreateInjector() {
      MyOtherLangLanguageStandaloneSetup.doSetup
      return super.internalCreateInjector
    }
  }

  @RunWith(XtextRunner)
  @InjectWith(MyLanguageWithDependenciesInjectorProvider)
  class YourTest {
    ...
  }
```

You should not put injector creation for referenced languages in your standalone setup. Note that for the headless code generation use case, the Maven plug-in is configured with multiple setups, so usually there is no problem there.

You may also need to initialize 'import'-ed ecore models that are not generated by your Xtext language. This should be done by using an explicit *MyModelPackage.eINSTANCE.getName();* in the *doSetup()* method of your respective language's StandaloneSetup class. Note that it is strongly recommended to follow this pattern instead of just using *@Before* methods in your \*Test class, as due to internal technical reasons that won't work anymore as soon as you have more than just one *@Test*.

```xtend
  class MyLanguageStandaloneSetup extends MyLanguageStandaloneSetupGenerated {

    def static void doSetup() {
      if (!EPackage.Registry.INSTANCE.containsKey(MyPackage.eNS_URI))
          EPackage.Registry.INSTANCE.put(MyPackage.eNS_URI, MyPackage.eINSTANCE);
      new MyLanguageStandaloneSetup().createInjectorAndDoEMFRegistration
    }

  }
```

This only applies to referencing dependencies to 'import'-ed Ecore models and languages based on them which may be used in the test. The inherited dependencies from mixed-in grammars are automatically listed in the generated super class already, and nothing needs to be done for those.

---

**[Next Chapter: IDE Concepts](304_ide_concepts.html)**
