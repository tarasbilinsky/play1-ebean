package play.modules.ebean;

import io.ebean.event.BeanPostLoad;

public class EbeanPostLoader implements BeanPostLoad
{

    @Override
    public boolean isRegisterFor(Class<?> cls)
    {
        return EbeanSupport.class.isAssignableFrom(cls);
    }

    /**
     * Since 1.0.7
     * Ebean 6.3.1 moved <code>BeanPersistController.postLoad()</code> to {@link io.ebean.event.BeanPostLoad} interface
     * @param bean
     */
    @Override
    public void postLoad(Object bean)
    {
        ((EbeanSupport) bean).afterLoad();
    }
}
