package de.balpha.streamwiggle;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamFoldingBuilder extends FoldingBuilderEx {


    private static boolean isStreamCall(PsiMethodCallExpression mce) {
        var method = mce.resolveMethod();
        if (method == null) {
            return false;
        }
        if (!method.getName().equals("stream")) {
            return false;
        }
        if (method.getParameterList().getParametersCount() > 0) {
            return false;
        }
        var returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }
        var erased = TypeConversionUtil.erasure(returnType);
        return erased.getCanonicalText().equals("java.util.stream.Stream");
    }

    private static boolean isCollectCall(PsiMethodCallExpression mce) {
        var method = mce.resolveMethod();
        if (method == null) {
            return false;
        }
        if (!method.getName().equals("collect")) {
            return false;
        }
        var erased = TypeConversionUtil.erasure(mce.getMethodExpression().getQualifierExpression().getType());
        return erased.getCanonicalText().equals("java.util.stream.Stream") || erased.getCanonicalText().equals("org.jooq.lambda.Seq");
    }

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        FoldingGroup group = FoldingGroup.newGroup("simple");

        List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
        Collection<PsiLiteralExpression> literalExpressions =
                PsiTreeUtil.findChildrenOfType(root, PsiLiteralExpression.class);

        var streamCalls = PsiTreeUtil.findChildrenOfType(root, PsiMethodCallExpression.class)
                .stream().filter(StreamFoldingBuilder::isStreamCall)
                .collect(Collectors.toList());

        for (var streamCall : streamCalls) {
            descriptors.add(new FoldingDescriptor(streamCall.getNode(),
                    new TextRange(streamCall.getMethodExpression().getReferenceNameElement().getNode().getTextRange().getStartOffset(),
                            streamCall.getTextRange().getEndOffset()),
                    group, "~"));
        }

        var collectCalls = PsiTreeUtil.findChildrenOfType(root, PsiMethodCallExpression.class)
                .stream().filter(StreamFoldingBuilder::isCollectCall)
                .collect(Collectors.toList());

        for (var collectCall : collectCalls) {

            var start = collectCall.getMethodExpression().getReferenceNameElement().getNode().getTextRange().getStartOffset() - 1;
            var end = collectCall.getMethodExpression().getReferenceNameElement().getNode().getTextRange().getEndOffset();

            var args = collectCall.getArgumentList().getExpressions();
            if (args.length == 1) {
                end = collectCall.getArgumentList().getNode().getTextRange().getStartOffset() + 1;

                descriptors.add(new FoldingDescriptor(collectCall.getNode(),
                        new TextRange(collectCall.getArgumentList().getNode().getTextRange().getEndOffset() - 1,
                                collectCall.getArgumentList().getNode().getTextRange().getEndOffset()),
                        group, ""));

                if (args[0] instanceof PsiMethodCallExpression) {
                    var collector = (PsiMethodCallExpression) args[0];
                    var qualifier = collector.getMethodExpression().getQualifierExpression();
                    if (qualifier != null && (qualifier.getText().equals("Collectors") || qualifier.getText().equals("MoreCollectors"))) {
                        descriptors.add(new FoldingDescriptor(qualifier.getNode(),
                                new TextRange(qualifier.getTextRange().getStartOffset(),
                                        qualifier.getTextRange().getEndOffset() + 1),
                                group, ""));
                    }
                }
            }

            descriptors.add(new FoldingDescriptor(collectCall.getNode(),
                    new TextRange(start, end),
                    group, "~>"));

        }
        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return "HELLO";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }
}