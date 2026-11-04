import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class Check {
    public static void main(String[] a) throws Exception {
        ZipFile jar = new ZipFile(a[0]);
        int bad = 0;
        for (String line : Files.readAllLines(Path.of(a[1]))) {
            String[] p = line.split("\t", -1);
            String where = p[0], cls = p[1].replace('.', '/'), meth = p[2], kind = p[3], atv = p[4], att = p[5];
            int ord = Integer.parseInt(p[6]);
            ZipEntry e = jar.getEntry(cls + ".class");
            if (e == null) { System.out.println("NOCLASS " + where + " " + cls); bad++; continue; }
            ClassNode cn = new ClassNode();
            new ClassReader(jar.getInputStream(e)).accept(cn, 0);
            String m = meth.replaceFirst("^L[^;]+;", "");
            String name = m.contains("(") ? m.substring(0, m.indexOf('(')) : m;
            String desc = m.contains("(") ? m.substring(m.indexOf('(')) : null;
            List<MethodNode> ms = new ArrayList<>();
            for (MethodNode mn : cn.methods) if (mn.name.equals(name) && (desc == null || mn.desc.equals(desc))) ms.add(mn);
            if (ms.isEmpty()) { System.out.println("NOMETHOD " + where + " " + cls + "." + meth); bad++; continue; }
            if (!(atv.equals("INVOKE") || atv.equals("INVOKE_ASSIGN") || atv.equals("NEW") || atv.equals("FIELD")) || att.isEmpty()) continue;
            String tOwner = null, tName, tDesc;
            if (atv.equals("NEW")) {
                if (att.startsWith("(")) { tDesc = att.substring(0, att.indexOf(')') + 1) + "V"; tOwner = att.substring(att.indexOf(")L") + 2, att.length() - 1); }
                else { tOwner = att.startsWith("L") ? att.substring(1, att.length() - 1) : att; tDesc = null; }
                tName = "<init>";
            } else if (atv.equals("FIELD")) {
                tOwner = att.substring(1, att.indexOf(';')); String r = att.substring(att.indexOf(';') + 1);
                tName = r.substring(0, r.indexOf(':')); tDesc = r.substring(r.indexOf(':') + 1);
            } else {
                String r = att;
                if (r.startsWith("L")) { tOwner = r.substring(1, r.indexOf(';')); r = r.substring(r.indexOf(';') + 1); }
                tName = r.substring(0, r.indexOf('(')); tDesc = r.substring(r.indexOf('('));
            }
            int count = 0;
            for (MethodNode mn : ms) {
                int c = 0;
                for (AbstractInsnNode in : mn.instructions) {
                    if (atv.equals("FIELD") && in instanceof FieldInsnNode f) {
                        if (f.owner.equals(tOwner) && f.name.equals(tName) && f.desc.equals(tDesc)) c++;
                    } else if (in instanceof MethodInsnNode mi && !atv.equals("FIELD")) {
                        if (mi.name.equals(tName) && (tOwner == null || mi.owner.equals(tOwner)) && (tDesc == null || mi.desc.equals(tDesc))) c++;
                    }
                }
                count = Math.max(count, c);
            }
            boolean ok = ord < 0 ? count > 0 : count > ord;
            if (!ok) { System.out.println("MISS " + where + " " + kind + " in " + cls + "." + meth + " -> " + att + " ord " + ord + " found " + count); bad++; }
        }
        System.out.println("bad " + bad);
    }
}
