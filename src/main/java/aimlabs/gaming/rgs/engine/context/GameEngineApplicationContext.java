package aimlabs.gaming.rgs.engine.context;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.engine.artifact.ArtifactMetaData;
import aimlabs.gaming.rgs.engine.discovery.RGSEngineProperties;
import aimlabs.gaming.rgs.engine.discovery.RGSServiceDiscovery;
import in.aimlabs.cloud.classloaders.AimlObjectFactory;
import in.aimlabs.cloud.classloaders.jar.JarClassLoader;
import in.aimlabs.gaming.engine.api.model.ForceGameResult;
import in.aimlabs.gaming.engine.api.model.GameConfiguration;
import in.aimlabs.gaming.engine.api.model.GameEngineRequest;
import in.aimlabs.gaming.engine.api.model.GameEngineResponse;
import in.aimlabs.gaming.engine.api.service.GameEngine;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

@Slf4j
@Data
public class GameEngineApplicationContext {

    private final Path engineJarPath;
    private final ApplicationContext applicationContext;
    private DefaultListableBeanFactory beanFactory;
    private AutowiredAnnotationBeanPostProcessor bpp;
    private final Manifest manifest;
    private Path forcedResultsJarPath;
    private Manifest forcedResultsManifest;
    private JarClassLoader jcl;
    private Object engineBean;
    private HashMap<Pointcut, Class> aspectsMap;
    RGSServiceDiscovery rgsServiceDiscovery;
    private boolean engineVerified;


    public GameEngineApplicationContext(ArtifactMetaData artifactMetaData, ApplicationContext applicationContext, RGSServiceDiscovery rgsServiceDiscovery) {
        RGSEngineProperties rgsEngineProperties = rgsServiceDiscovery.getRgsEngineProperties();
        this.engineJarPath = Path.of(rgsEngineProperties.getDir()+"/"+artifactMetaData.getName());
        this.applicationContext = applicationContext;
        this.jcl = new JarClassLoader(this.getClass().getClassLoader());
        this.jcl.add(engineJarPath.toFile().getPath());
        this.rgsServiceDiscovery = rgsServiceDiscovery;
        manifest = jcl.getClasspathResources().getJarManifest(engineJarPath.toAbsolutePath().toString());
        List<String> gameConfigurations = this.jcl.getLoadedResources().keySet().stream().filter(s -> s.endsWith(".json")).collect(Collectors.toList());
        String buildVersion = manifest.getMainAttributes().getValue("Build-Revision");

        if(!rgsServiceDiscovery.test(engineJarPath.toFile(), artifactMetaData.getDigest(), gameConfigurations)){
            return;
        }

        if (manifest != null) {
            String version = manifest.getMainAttributes().getValue("Build-Revision");
            String mainClass = manifest.getMainAttributes().getValue("Main-Class");

            if(mainClass!=null && mainClass.contains("table")) {
                forcedResultsJarPath = Path.of(engineJarPath.toFile()
                        .getAbsolutePath().replace(engineJarPath.toFile().getName(), "game-engine-table-forced-results-" + version + ".jar"));
                log.info("Forced results jar {}", forcedResultsJarPath.toAbsolutePath());
                if (forcedResultsJarPath.toFile().exists()) {
                    log.info("Including forced results jar {}", forcedResultsJarPath);
                    jcl.add(forcedResultsJarPath.toFile().getPath());
                    forcedResultsManifest = jcl.getClasspathResources()
                            .getJarManifest(forcedResultsJarPath.toAbsolutePath().toString());
                }
            }else{
                forcedResultsJarPath = Path.of(engineJarPath.toFile()
                        .getAbsolutePath().replace(engineJarPath.toFile().getName(), "game-engine-slots-forcedresults-" + version + ".jar"));
                log.info("Forced results jar {}", forcedResultsJarPath.toAbsolutePath());
                if (forcedResultsJarPath.toFile().exists()) {
                    log.info("Including forced results jar {}", forcedResultsJarPath);
                    jcl.add(forcedResultsJarPath.toFile().getPath());
                    forcedResultsManifest = jcl.getClasspathResources()
                            .getJarManifest(forcedResultsJarPath.toAbsolutePath().toString());
                }
            }
        }

        this.beanFactory = new DefaultListableBeanFactory(applicationContext.getAutowireCapableBeanFactory());
        bpp = new AutowiredAnnotationBeanPostProcessor();
        this.beanFactory.addBeanPostProcessor(bpp);
        bpp.setBeanFactory(this.beanFactory);
        this.beanFactory.setAutowireCandidateResolver(new AutowireCandidateResolver() {
            
            public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
                if (descriptor.getDependencyType().getPackageName().startsWith("in.aimlabs.gaming.engine")) {
                    //log.info("dependency name: {}", descriptor.getDependencyName());
                    if (descriptor.getDependencyName() != null && beanFactory.containsBean(descriptor.getDependencyName())) {
                        return beanFactory.getBean(descriptor.getDependencyName());
                    }
                    AimlObjectFactory factory = AimlObjectFactory.getInstance();
                    Object object = factory.create(GameEngineApplicationContext.this.jcl, descriptor.getDependencyType().getName());
                    //beanFactory.registerSingleton(Objects.requireNonNull(descriptor.getDependencyName()), object);
                    bpp.processInjection(object);

                    return createAspectJProxy(object);
                }
                return applicationContext.getAutowireCapableBeanFactory().resolveDependency(descriptor, beanName);
            }
        });

        engineVerified = true;
    }

    // Our method
    static String toCamelCase(String s) {

        // create a StringBuilder to create our output string
        StringBuilder sb = new StringBuilder();

        // determine when the next capital letter will be
        boolean nextCapital = true;

        // loop through the string
        for (int i = 0; i < s.length(); i++) {

            // if the current character is a letter
            if (Character.isLetter(s.charAt(i))) {

                // get the current character
                char tmp = s.charAt(i);

                // make it a capital if required
                if (nextCapital) tmp = Character.toLowerCase(tmp);

                // add it to our output string
                sb.append(tmp);

                // make sure the next character isn't a capital
                nextCapital = false;

            } else {
                // otherwise the next letter should be a capital
                nextCapital = true;
            }
        }

        // return our output string
        return sb.toString();
    }

    @SneakyThrows
    public Object start() {
        if(!engineVerified)
            return null;

        AimlObjectFactory factory = AimlObjectFactory.getInstance();
        String engineClass = (String) manifest.getMainAttributes().get(Attributes.Name.MAIN_CLASS);

        if (engineClass == null) {
            log.warn("found no {} in manifest. skipping initialising {}", Attributes.Name.MAIN_CLASS, engineJarPath.toAbsolutePath());
            return null;
        }

        Object engine = loadAspects(factory, engineClass);

        @SuppressWarnings("rawtypes")
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(GameEngine.class, () -> (GameEngine) engine)
                .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME)
                .setInitMethodName("init")
                .setLazyInit(false)
                .setScope(BeanDefinition.SCOPE_SINGLETON).getBeanDefinition();

        String beanName = toCamelCase(engine.getClass().getSimpleName());
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
        bpp.processInjection(engine);
        engineBean = this.beanFactory.getBean(beanName);

        Object bean = createAspectJProxy(engineBean);
        this.applicationContext.getAutowireCapableBeanFactory().applyBeanPostProcessorsBeforeInitialization(bean, beanName);


        GameEngine<GameEngineRequest, GameEngineResponse> gameEngineService = ((GameEngine<GameEngineRequest, GameEngineResponse>) bean);
        for (Map.Entry<String, ? extends GameConfiguration> entry : gameEngineService.supportedGameConfigurations().entrySet()) {
            rgsServiceDiscovery.addGameEngineModule(entry.getKey(), gameEngineService.getGameEngineModule());
            rgsServiceDiscovery.addEngineService(entry.getKey(), gameEngineService);
        }

        return bean;
    }

    private Object loadAspects(AimlObjectFactory factory, String engineClass) throws IOException {
        Object engine = factory.create(jcl, engineClass);
        aspectsMap = new HashMap<>();
        InputStream is = null;
        try {
            is = this.jcl.getResourceAsStream("META-INF/spring.factories");
            Properties properties = new Properties();
            properties.load(is);

            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                if (!((String) entry.getKey()).contains("in.aimlabs.gaming.engine.api.model.ForceGameResult")) {
                    continue;
                }
                String[] factoryImplementationNames =
                        StringUtils.commaDelimitedListToStringArray((String) entry.getValue());
                for (String factoryImplementationName : factoryImplementationNames) {
                    Class<?> aspect = this.jcl.loadClass(factoryImplementationName);
                    Arrays.stream(aspect.getDeclaredMethods())
                            .map(method -> AnnotationUtils.findAnnotation(method, Pointcut.class))
                            .filter(Objects::nonNull)
                            .forEach(pointcut -> {
                                log.info("Found aspect {}", aspect);
                                aspectsMap.put(pointcut, aspect);
                            });
                }
            }
        } catch (Exception ex) {
            assert is != null;
            is.close();

            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Jar " + engineJarPath + " initialisation failed", ex);
        }
        return engine;
    }

    private Object createAspectJProxy(Object targetObject) {

        if (aspectsMap == null || aspectsMap.isEmpty())
            return targetObject;

        List<Object> aspects = aspectsMap.keySet()
                .stream()
                .filter(pointcut -> {
                    log.info("Evaluating pointcut {} for dependency class {}", pointcut, targetObject.getClass().getName());
                    boolean matched =  pointcut.value().contains(targetObject.getClass().getName())
                                       || (pointcut.value().contains("execution(public * in.aimlabs.gaming.engine.*.service.*.play(..))")
                                           && targetObject instanceof GameEngine)
                                       || (pointcut.value().contains("execution(public * in.aimlabs.gaming.engine.table.*.service.*.play(..))")
                                           && targetObject instanceof GameEngine);

                    /*AspectJExpressionPointcut pointcutExpression = new AspectJExpressionPointcut();
                    pointcutExpression.setExpression(pointcut.value());*/
                    if (matched)
                        log.info("Pointcut {} matched for dependency class. {}", pointcut, targetObject.getClass().getName());

                    return matched;
                })
                .map(key -> aspectsMap.get(key))
                .map(aspectClass -> {
                    String aspectBeanName = toCamelCase(aspectClass.getSimpleName());
                    Object aspectObject;
                    if (!beanFactory.containsBean(aspectBeanName)) {
                        AimlObjectFactory factory = AimlObjectFactory.getInstance();
                        aspectObject = factory.create(jcl, aspectClass.getName());
                    } else {
                        aspectObject = beanFactory.getBean(aspectBeanName);
                    }
                    return aspectObject;
                })
                .collect(Collectors.toList());

        if (aspects.size() > 1)
            log.warn("More than one aspect matched for dependency class {}", targetObject.getClass().getName());

        if (aspects.size() > 0) {
            Object aspectObject = aspects.get(0);
            String aspectBeanName = toCamelCase(aspectObject.getClass().getSimpleName());
            bpp.processInjection(aspectObject);
            AspectJProxyFactory proxyFactory = new AspectJProxyFactory(targetObject);
            //proxyFactory.addAspect(SecurityManager.class);
            proxyFactory.addAspect(aspectObject);
            log.info("Aspect {} created.", aspectObject.getClass().getName());
            if (aspectObject instanceof ForceGameResult forceGameResult) {
                forceGameResult.setGameEngineService((GameEngine) targetObject);
                this.applicationContext.getAutowireCapableBeanFactory()
                        .applyBeanPostProcessorsBeforeInitialization(aspectObject, aspectBeanName);
            }
            return proxyFactory.getProxy();
        } else {
            return targetObject;
        }

    }
}
