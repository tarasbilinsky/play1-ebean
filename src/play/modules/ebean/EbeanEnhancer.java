package play.modules.ebean;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.util.HashSet;
import java.util.Set;

import io.ebean.Query;
import io.ebean.enhance.Transformer;
import io.ebean.enhance.common.AgentManifest;
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
  static ClassFileTransformer transformer = new Transformer(new PlayClassBytesReader(), "transientInternalFields=true;debug=1", new AgentManifest(Play.classloader));


  @Override
  public void enhanceThisClass(ApplicationClass applicationClass) throws Exception
  {

    if (applicationClass.name.contains("classloading")) return;
    // Ebean transformations
    byte[] buffer = transformer.transform(Play.classloader, applicationClass.name.replace("/", "."), null, null, applicationClass.enhancedByteCode);
    if (buffer != null) applicationClass.enhancedByteCode = buffer;

    CtClass ctClass = makeClass(applicationClass);

    // Enhance only JPA entities
    if (!hasAnnotation(ctClass, "jakartax.persistence.Entity")) {
      return;
    }

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

    applicationClass.enhancedByteCode = ctClass.toBytecode();
    ctClass.defrost();
    Logger.debug("EBEAN: Class '%s' has been enhanced",ctClass.getName());
  }

}
