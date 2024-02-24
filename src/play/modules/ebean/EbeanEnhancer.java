package play.modules.ebean;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.util.HashSet;
import java.util.Set;

import io.ebean.Query;
import io.ebean.enhance.Transformer;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;
import play.exceptions.UnexpectedException;

import io.ebean.enhance.common.ClassBytesReader;
import io.ebean.enhance.common.InputStreamTransform;

public class EbeanEnhancer extends Enhancer
{
  static ClassFileTransformer transformer = new Transformer(Play.classloader, "transientInternalFields=true;debug=1");

//  @Override
//  protected String getCommonSuperClass(String type1, String type2)
//  {
//    try {
//      // First put all super classes of type1, including type1 (starting with type2 is equivalent)
//      Set<String> superTypes1 = new HashSet<String>();
//      String s = type1;
//      superTypes1.add(s);
//      while (!"java/lang/Object".equals(s)) {
//        s = getSuperType(s);
//        superTypes1.add(s);
//      }
//      // Then check type2 and each of it's super classes in sequence if it is in the set
//      // First match is the common superclass.
//      s = type2;
//      while (true) {
//        if (superTypes1.contains(s)) return s;
//        s = getSuperType(s);
//      }
//    } catch (Exception e) {
//      throw new RuntimeException(e.toString());
//    }
//  }

//  private String getSuperType(String type) throws ClassNotFoundException
//  {
//    ApplicationClass ac = Play.classes.getApplicationClass(type.replace('/', '.'));
//    try {
//      return ac != null ? new ClassReader(ac.enhancedByteCode).getSuperName() : new ClassReader(type).getSuperName();
//    } catch (IOException e) {
//      throw new ClassNotFoundException(type);
//    }
//  }


  @Override
  public void enhanceThisClass(ApplicationClass applicationClass) throws Exception
  {
    // Ebean transformations
    byte[] buffer = transformer.transform(Play.classloader, applicationClass.name.replace('/', '.'), null, null, applicationClass.enhancedByteCode);
    if (buffer != null) applicationClass.enhancedByteCode = buffer;

    CtClass ctClass = makeClass(applicationClass);
 
    if (!ctClass.subtypeOf(classPool.get(io.ebean.Model.class.getCanonicalName()))) {
      // We don't want play style enhancements to happen to classes other than subclasses of EbeanSupport
      return;
    }

    // Enhance only JPA entities
    if (!hasAnnotation(ctClass, "jakartax.persistence.Entity")) {
      return;
    }

    String entityName = ctClass.getName();

    // Add a default constructor if needed
    try {
      boolean hasDefaultConstructor = false;
      for (CtConstructor constructor : ctClass.getConstructors()) {
        if (constructor.getParameterTypes().length == 0) {
          hasDefaultConstructor = true;
          break;
        }
      }
      if (!hasDefaultConstructor && !ctClass.isInterface()) {
        CtConstructor defaultConstructor = CtNewConstructor.make("private " + ctClass.getSimpleName() + "() {}", ctClass);
        ctClass.addConstructor(defaultConstructor);
      }
    } catch (Throwable t) {
      Logger.error(t, "Error in EbeanEnhancer");
      throw new UnexpectedException("Error in EbeanEnhancer", t);
    }

    // create     
    ctClass.addMethod(CtMethod.make("public static play.modules.ebean.EbeanSupport create(String name, play.mvc.Scope.Params params) { return create(" + entityName + ".class,name, params.all(), null); }",ctClass));

    // count
    ctClass.addMethod(CtMethod.make("public static long count() { return (long) ebean().createQuery(" + entityName + ".class).findCount(); }", ctClass));
    ctClass.addMethod(CtMethod.make("public static long count(String query, Object[] params) { return (long) createQuery(" + entityName + ".class,query,params).findCount(); }", ctClass));

    // findAll
    ctClass.addMethod(CtMethod.make("public static java.util.List findAll() { return ebean().createQuery(" + entityName + ".class).findList(); }", ctClass));

    // findById
    ctClass.addMethod(CtMethod.make("public static play.modules.ebean.EbeanSupport findById(Object id) { return (" + entityName + ") ebean().find(" + entityName + ".class, id); }", ctClass));

    // findOne
    ctClass.addMethod(CtMethod.make("public static play.modules.ebean.EbeanSupport findOne(String query, Object[] params) { return (" + entityName + ") createQuery(" + entityName + ".class,query,params).findOne(); }", ctClass));
    // findUnique
    ctClass.addMethod(CtMethod.make("public static play.modules.ebean.EbeanSupport findUnique(String query, Object[] params) { return (" + entityName + ") createQuery(" + entityName + ".class,query,params).findOne(); }", ctClass));

    // find
    ctClass.addMethod(CtMethod.make("public static " + Query.class.getCanonicalName() + " find(String query, Object[] params) { return createQuery(" + entityName + ".class,query,params); }", ctClass));

    // all
    ctClass.addMethod(CtMethod.make("public static " + Query.class.getCanonicalName() + " all() { return ebean().createQuery(" + entityName + ".class); }", ctClass));

    // delete
    ctClass.addMethod(CtMethod.make("public static int delete(String query, Object[] params) { return createDeleteQuery(" + entityName + ".class,query,params).execute(); }", ctClass));

    // deleteAll
    ctClass.addMethod(CtMethod.make("public static int deleteAll() { return  createDeleteQuery(" + entityName + ".class,null,null).execute(); }", ctClass));

    // Done.
    applicationClass.enhancedByteCode = ctClass.toBytecode();
    ctClass.defrost();
    Logger.debug("EBEAN: Class '%s' has been enhanced",ctClass.getName());
  }

  static class PlayClassBytesReader implements ClassBytesReader
  {

    public byte[] getClassBytes(String className, ClassLoader classLoader)
    {
      ApplicationClass ac = Play.classes.getApplicationClass(className.replace("/", "."));
      return ac != null ? ac.enhancedByteCode : getBytesFromClassPath(className);
    }

    private byte[] getBytesFromClassPath(String className)
    {
      String resource = className + ".class";
      byte[] classBytes = null;
      InputStream is = Play.classloader.getResourceAsStream(resource);
      try {
        classBytes = InputStreamTransform.readBytes(is);
      } catch (IOException e) {
        throw new RuntimeException("IOException reading bytes for " + className, e);
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (IOException e) {
            throw new RuntimeException("Error closing InputStream for " + className, e);
          }
        }
      }
      return classBytes;
    }

  }

}
