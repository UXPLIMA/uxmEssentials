package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns an expression string into a flat token list the {@link Parser} walks. The lexer recognises only the
 * sandbox's vocabulary. Numbers, quoted strings, bare identifiers (function names and keywords), parentheses,
 * commas, and the fixed operator set. Any character outside that vocabulary is an error, which is the first line
 * of the sandbox: there is no syntax through which to reach a method call, a field, or the host runtime.
 */
final class Lexer {

    /** The two-character operators, matched before their single-character prefixes so {@code <=} beats {@code <}. */
    private static final Set<String> TWO_CHAR = Set.of("==", "!=", "<=", ">=", "&&", "||");

    /** The single-character operators (and {@code !}, the unary boolean negation). */
    private static final String ONE_CHAR = "+-*/%^<>!";

    private final String src;

    private int pos;

    Lexer(String src) {
        this.src = src;
    }

    List<Token> tokenize() throws ExpressionException {
        List<Token> tokens = new ArrayList<>();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else {
                tokens.add(next(c));
            }
        }
        tokens.add(Token.of(Token.Kind.END, ""));
        return tokens;
    }

    private Token next(char c) throws ExpressionException {
        if (Character.isDigit(c) || c == '.') {
            return number();
        }
        if (c == '"' || c == '\'') {
            return string(c);
        }
        if (Character.isLetter(c) || c == '_') {
            return identifier();
        }
        return operator();
    }

    private Token number() throws ExpressionException {
        int start = pos;
        boolean dot = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '.') {
                if (dot) {
                    break;
                }
                dot = true;
            } else if (!Character.isDigit(c)) {
                break;
            }
            pos++;
        }
        String text = src.substring(start, pos);
        try {
            return Token.number(Double.parseDouble(text));
        } catch (NumberFormatException notNumeric) {
            throw new ExpressionException("malformed number: " + text);
        }
    }

    private Token string(char quote) throws ExpressionException {
        int start = ++pos;
        while (pos < src.length() && src.charAt(pos) != quote) {
            pos++;
        }
        if (pos >= src.length()) {
            throw new ExpressionException("unterminated string literal");
        }
        String text = src.substring(start, pos);
        pos++;
        return Token.of(Token.Kind.STRING, text);
    }

    private Token identifier() {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        return Token.of(Token.Kind.IDENT, src.substring(start, pos));
    }

    private Token operator() throws ExpressionException {
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            return Token.of(Token.Kind.LPAREN, "(");
        }
        if (c == ')') {
            pos++;
            return Token.of(Token.Kind.RPAREN, ")");
        }
        if (c == ',') {
            pos++;
            return Token.of(Token.Kind.COMMA, ",");
        }
        return symbol();
    }

    private Token symbol() throws ExpressionException {
        if (pos + 1 < src.length()) {
            String two = src.substring(pos, pos + 2);
            if (TWO_CHAR.contains(two)) {
                pos += 2;
                return Token.of(Token.Kind.OPERATOR, two);
            }
        }
        char c = src.charAt(pos);
        if (ONE_CHAR.indexOf(c) < 0) {
            throw new ExpressionException("unexpected character: " + c);
        }
        pos++;
        return Token.of(Token.Kind.OPERATOR, String.valueOf(c));
    }
}
