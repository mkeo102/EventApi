package dev.mkeo102.eventApi;


import dev.mkeo102.logger.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;


/**
 * A Class<?> for dispatching events to all methods that are subscribed to it and request that specific event via the {@link EventTarget} annotation
 */
//@SuppressWarnings("unchecked")
public class EventDispatcher {

    static Logger LOGGER = Logger.getLogger(EventDispatcher.class, false);

    Map<Class<Event>, List<MethodInfo>> eventMap;
    Map<String,List<Object>> parentMethods;
    Map<String,MethodInfo> infos;

    public EventDispatcher(){
        eventMap = new HashMap<>();
        parentMethods = new HashMap<>();
        infos = new HashMap<>();
    }

    private boolean isValidMethod(Method method){
        return method.getParameterCount() == 1 && Event.class.isAssignableFrom(method.getParameterTypes()[0]);
    }

    private static String convertMethod(Class<?> clazz, Method method){
        return String.format("%s#%s%s", clazz.getName(),method.getName(), getMethodDescriptor(method));
    }

    public void register(Object object){
        Class<?> clazz = object.getClass();
        Arrays.stream(clazz.getDeclaredMethods()).filter(mn -> mn.isAnnotationPresent(EventTarget.class)).filter(this::isValidMethod).forEach(mn -> {
            Class<Event> e = (Class<Event>) mn.getParameterTypes()[0];

            eventMap.computeIfAbsent(e, k -> new ArrayList<>());

            MethodInfo info;

            // Static methods
            if(Modifier.isStatic(mn.getModifiers())){



                if(infos.containsKey(convertMethod(clazz,mn))){
                    LOGGER.warning("Method: {}{} in class: {} is already registered to event : {}",  mn.getName(), getMethodDescriptor(mn), clazz.getName(),e);
                    return;
                } else {
                    info = new MethodInfo(mn,clazz);
                    info.addOwnerObject(object);
                    infos.put(convertMethod(clazz,mn),info);
                    eventMap.get(e).add(info);
                }

            } else {

                if(infos.containsKey(convertMethod(clazz,mn))){

                    if(infos.get(convertMethod(clazz,mn)).ownerObjects.contains(object)) {
                        LOGGER.warning("Method: {}{} in class: {} is already registered to object : {}", mn.getName(), getMethodDescriptor(mn), clazz.getName(), e);
                        return;
                    }

                    info = infos.get(convertMethod(clazz,mn));
                    info.addOwnerObject(object);
                } else {
                    info = new MethodInfo(mn,clazz);
                    info.addOwnerObject(object);
                    infos.put(convertMethod(clazz,mn),info);
                    eventMap.get(e).add(info);
                }

            }


            LOGGER.debug("Registered method: {}{} from class : {} to event : {}", mn.getName(), getMethodDescriptor(mn), clazz.getName(), e.getName());

        });
    }

    public void unregister(Class<?> clazz){
        Arrays.stream(clazz.getDeclaredMethods()).filter(mn -> mn.isAnnotationPresent(EventTarget.class)).filter(this::isValidMethod).forEach(mn ->{
            Class<Event> e = (Class<Event>) mn.getParameterTypes()[0];

            if(eventMap.get(e) == null) {
                LOGGER.debug("No methods for event: {} when unregistering class: {}.", e,clazz);
                return;
            }

            if(!infos.containsKey(convertMethod(clazz,mn))){
                LOGGER.debug("No MethodInfo for method: {}{} in class: {} for event : {}",mn.getName(),getMethodDescriptor(mn), clazz.getName(),e.getName());
            }





        });
    }

    public void unregister(Object object){
        infos.values().forEach(info -> {
            if(info.ownerObjects != null)
                info.ownerObjects.remove(object);
        });
    }


    public void call(Event e){
        List<MethodInfo> methods = eventMap.get(e.getClass());

        if(methods == null || methods.isEmpty())
            return;

        methods.forEach(methodInfo -> {
            Method method = methodInfo.method;

            try {
                method.setAccessible(true);
                if(methodInfo.ownerObjects == null){
                    method.invoke(null, e);
                } else {
                    for (Object o : methodInfo.ownerObjects){
                        try {
                            method.invoke(o, e);
                        } catch (Exception ex) {
                            LOGGER.warning("Failed to call non-static method: {}{} with object: {}", method.getName(), getMethodDescriptor(method), o);
                        }
                    }
                }
            } catch (Exception f){
                LOGGER.warning("Failed to call event: {} on method: {}." , e, method.getName());
            }
        });

    }

    static class MethodInfo{
        Method method;
        List<Object> ownerObjects;
        Class<?> owner;
        String converted;

        public MethodInfo(Method method,Class<?> clazz){
            this.method = method;
            this.owner = clazz;
            this.converted = convertMethod(clazz,method);
            this.ownerObjects = Modifier.isStatic(method.getModifiers()) ? null : new ArrayList<>();
        }

        public void addOwnerObject(Object ownerObj){
            if(this.ownerObjects == null)
                throw new IllegalStateException(String.format("Attempting to treat a static method: %s as non-static!", this));
            this.ownerObjects.add(ownerObj);
        }

        @Override
        public String toString(){
            return converted;
        }

    }


    // From here on these methods are taken from ASM's Type class. I did this to avoid having this depend on the large lib of asm and to avoid it being outdated often.

    /**
     * Returns the descriptor corresponding to the given method.
     *
     * @param m a {@link Method Method} object.
     * @return the descriptor of the given method.
     */
    public static String getMethodDescriptor(final Method m) {
        Class<?>[] parameters = m.getParameterTypes();
        StringBuffer buf = new StringBuffer();
        buf.append('(');
        for (int i = 0; i < parameters.length; ++i) {
            getDescriptor(buf, parameters[i]);
        }
        buf.append(')');
        getDescriptor(buf, m.getReturnType());
        return buf.toString();
    }

    /**
     * Returns the descriptor corresponding to the given Java type.
     *
     * @param c an object class, a primitive class or an array class.
     * @return the descriptor corresponding to the given class.
     */
    public static String getDescriptor(final Class<?> c) {
        StringBuffer buf = new StringBuffer();
        getDescriptor(buf, c);
        return buf.toString();
    }
    
    /**
     * Appends the descriptor of the given class to the given string buffer.
     *
     * @param buf the string buffer to which the descriptor must be appended.
     * @param c the class whose descriptor must be computed.
     */
    private static void getDescriptor(final StringBuffer buf, final Class<?> c) {
        Class<?> d = c;
        while (true) {
            if (d.isPrimitive()) {
                char car;
                if (d == Integer.TYPE) {
                    car = 'I';
                } else if (d == Void.TYPE) {
                    car = 'V';
                } else if (d == Boolean.TYPE) {
                    car = 'Z';
                } else if (d == Byte.TYPE) {
                    car = 'B';
                } else if (d == Character.TYPE) {
                    car = 'C';
                } else if (d == Short.TYPE) {
                    car = 'S';
                } else if (d == Double.TYPE) {
                    car = 'D';
                } else if (d == Float.TYPE) {
                    car = 'F';
                } else /* if (d == Long.TYPE) */{
                    car = 'J';
                }
                buf.append(car);
                return;
            } else if (d.isArray()) {
                buf.append('[');
                d = d.getComponentType();
            } else {
                buf.append('L');
                String name = d.getName();
                int len = name.length();
                for (int i = 0; i < len; ++i) {
                    char car = name.charAt(i);
                    buf.append(car == '.' ? '/' : car);
                }
                buf.append(';');
                return;
            }
        }
    }


}
