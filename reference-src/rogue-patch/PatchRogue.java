import java.io.*; import java.util.*; import java.util.zip.*;
import org.objectweb.asm.*; import org.objectweb.asm.tree.*;
public class PatchRogue {
  public static void main(String[] a) throws Exception {
    try (ZipFile zf=new ZipFile(a[0]); ZipOutputStream zo=new ZipOutputStream(new FileOutputStream(a[1]))) {
      for (Enumeration<? extends ZipEntry> e=zf.entries(); e.hasMoreElements();) {
        ZipEntry ze=e.nextElement(); byte[] b=zf.getInputStream(ze).readAllBytes();
        if (ze.getName().equals("org/CobbleUtils/cobbleroguelike/compat/CobblemonBridge.class")) {
          ClassNode cn=new ClassNode(); new ClassReader(b).accept(cn,0); int n=0;
          for (MethodNode m: cn.methods) if (m.name.equals("createRogueCopy"))
            for (AbstractInsnNode i: m.instructions.toArray())
              if (i instanceof MethodInsnNode mi && mi.name.equals("initializeMoveset")) {
                Type[] ts=Type.getArgumentTypes(mi.desc); // objectref + args
                for (int k=0;k<ts.length;k++) m.instructions.insertBefore(i,new InsnNode(ts[ts.length-1-k].getSize()==2?Opcodes.POP2:Opcodes.POP));
                m.instructions.insertBefore(i,new InsnNode(Opcodes.POP));
                if (Type.getReturnType(mi.desc).getSize()==0) { m.instructions.remove(i); } else { m.instructions.set(i,new InsnNode(Opcodes.NOP)); }
                n++;
              }
          System.out.println("removed initializeMoveset calls: "+n+" desc seen");
          if(n!=1) throw new RuntimeException("expected 1");
          ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_FRAMES){ protected String getCommonSuperClass(String x,String y){return "java/lang/Object";}};
          cn.accept(cw); b=cw.toByteArray();
        }
        if (ze.getName().equals("fabric.mod.json")) b=new String(b).replace("\"version\": \"1.0-SNAPSHOT\"","\"version\": \"1.0-SNAPSHOT-pp1\"").getBytes();
        zo.putNextEntry(new ZipEntry(ze.getName())); zo.write(b); zo.closeEntry();
      }
    }
  }
}
