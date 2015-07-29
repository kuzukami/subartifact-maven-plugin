package jp.co.iidev.subartifact1.divider1.asmhack;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.signature.SignatureVisitor;

public class CollectingRemapper extends Remapper {

    public final Set<String> classesByClassName = new HashSet<String>();

    @Override
    protected SignatureVisitor createRemappingSignatureAdapter(SignatureVisitor v) {
        return new SignatureRemapper(v, this);
    }

    public String map(String pClassName) {
        classesByClassName.add(pClassName.replace('/', '.'));
        return pClassName;
    }
}