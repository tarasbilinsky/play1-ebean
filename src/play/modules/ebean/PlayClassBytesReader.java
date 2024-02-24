package play.modules.ebean;

import io.ebean.enhance.asm.ClassReader;
import io.ebean.enhance.common.ClassBytesReader;
import io.ebean.enhance.common.InputStreamTransform;
import play.Play;
import play.classloading.ApplicationClasses;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

class PlayClassBytesReader implements ClassBytesReader {

    public byte[] getClassBytes(String className, ClassLoader classLoader)
    {
        if (className.contains("classloading")) return null;
        if (className.contains("TypeBinder")) return null;
        ApplicationClasses.ApplicationClass ac = Play.classes.getApplicationClass(className.replace("/", "."));
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


    //TODO ? Class writer old code
//    protected String getCommonSuperClass(String type1, String type2)
//    {
//        try {
//            // First put all super classes of type1, including type1 (starting with type2 is equivalent)
//            Set<String> superTypes1 = new HashSet<String>();
//            String s = type1;
//            superTypes1.add(s);
//            while (!"java/lang/Object".equals(s)) {
//                s = getSuperType(s);
//                superTypes1.add(s);
//            }
//            // Then check type2 and each of it's super classes in sequence if it is in the set
//            // First match is the common superclass.
//            s = type2;
//            while (true) {
//                if (superTypes1.contains(s)) return s;
//                s = getSuperType(s);
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e.toString());
//        }
//    }
//
//    private String getSuperType(String type) throws ClassNotFoundException
//    {
//        ApplicationClasses.ApplicationClass ac = Play.classes.getApplicationClass(type.replace('/', '.'));
//        try {
//            return ac != null ? new ClassReader(ac.enhancedByteCode).getSuperName() : new ClassReader(type).getSuperName();
//        } catch (IOException e) {
//            throw new ClassNotFoundException(type);
//        }
//    }

}
