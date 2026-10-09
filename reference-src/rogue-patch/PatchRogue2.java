import java.io.*; import java.nio.file.*; import java.util.*; import java.util.zip.*;
import org.objectweb.asm.*; import org.objectweb.asm.tree.*;
public class PatchRogue2 {
  public static void main(String[] a) throws Exception {
    // a[0]=in a[1]=out a[2]=ShopList class dir
    try (ZipFile zf=new ZipFile(a[0]); ZipOutputStream zo=new ZipOutputStream(new FileOutputStream(a[1]))) {
      for (Enumeration<? extends ZipEntry> e=zf.entries(); e.hasMoreElements();) {
        ZipEntry ze=e.nextElement(); byte[] b=zf.getInputStream(ze).readAllBytes(); String n=ze.getName();
        if (n.equals("org/CobbleUtils/cobbleroguelike/ui/ShopMenus.class")) { b=hook(b,"shop","(Lnet/minecraft/class_3222;)V",false); b=hook(b,"category","(Lnet/minecraft/class_3222;II)V",false); }
        if (n.equals("org/CobbleUtils/cobbleroguelike/ui/RewardMenus.class")) { b=hook(b,"shop","(Lnet/minecraft/class_3222;)V",true); b=hook(b,"category","(Lnet/minecraft/class_3222;II)V",true); }
        if (n.equals("fabric.mod.json")) b=new String(b).replace("1.0-SNAPSHOT-pp1","1.0-SNAPSHOT-pp2").getBytes();
        zo.putNextEntry(new ZipEntry(n)); zo.write(b); zo.closeEntry();
      }
      File dir=new File(a[2]);
      for (File f: Objects.requireNonNull(dir.listFiles())) { if(!f.getName().endsWith(".class")) continue; zo.putNextEntry(new ZipEntry("org/CobbleUtils/cobbleroguelike/ui/"+f.getName())); zo.write(Files.readAllBytes(f.toPath())); zo.closeEntry(); }
    }
  }
  static byte[] hook(byte[] b,String name,String desc,boolean tok){
    ClassNode cn=new ClassNode(); new ClassReader(b).accept(cn,0); int n=0;
    for (MethodNode m: cn.methods) if (m.name.equals(name)&&m.desc.equals(desc)) {
      InsnList il=new InsnList(); LabelNode ok=new LabelNode();
      il.add(new VarInsnNode(Opcodes.ALOAD,0)); il.add(new InsnNode(tok?Opcodes.ICONST_1:Opcodes.ICONST_0));
      il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"org/CobbleUtils/cobbleroguelike/ui/ShopList","open","(Lnet/minecraft/class_3222;Z)Z",false));
      il.add(new JumpInsnNode(Opcodes.IFEQ,ok)); il.add(new InsnNode(Opcodes.RETURN)); il.add(ok);
      m.instructions.insert(il); n++; }
    if(n!=1) throw new RuntimeException("hook "+cn.name+"."+name+" found "+n);
    ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_FRAMES){ protected String getCommonSuperClass(String x,String y){return "java/lang/Object";}};
    cn.accept(cw); return cw.toByteArray();
  }
}
