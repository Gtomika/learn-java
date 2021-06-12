package com.gaspar.learnjava.formatter;

import java.util.regex.Pattern;

/**
 * Stores the regex-es used in the formatting process. None of the warnings generated by android studio
 * should be taken into consideration. Attempting to fix these warnings ruins the regex-es.
 * @author Gáspár Tamás
 */
@SuppressWarnings("all")
public abstract class RegexConstants {

    /**
     * Matches string and character literals in the code. Group 1 holds the matched literal. It won't match the color code
     * specifications inside the font tags.
     */
    static final Pattern TEXT_LITERAL_REGEX = Pattern.compile("(?<!\\<font color=)(([\"'].[^\\<^\\>]*?[\"']))(?!\\>)", Pattern.MULTILINE);

    /**
     * Matches numeric literals in the code, such as 1, -3.32, 2.3f. It wont match numbers that are inside words.
     */
    static final Pattern NUMERIC_LITERAL_REGEX = Pattern.compile("(?<![\"A-Za-z0-9#])(-?[0-9]+(.[0-9]+)?f?)(?![\"A-Za-z])");

    /**
     * Regex that matches Java primitive types. Also matches void. Group 2 ($2) holds the matched primitive.
     */
    static final Pattern PRIMITIVE_TYPE_REGEX = Pattern.compile("([^A-Z^a-z])(byte|short|int|long|float|double|boolean|char|void)(?![A-Za-z])", Pattern.MULTILINE);

    /**
     * Regex that matches java keywords. Group 2 ($2) holds the matched keyword.
     */
    static final Pattern KEYWORD_REGEX = Pattern.compile("([^A-Z^a-z])(abstract|continue|for|new|switch|assert|default|goto|package|synchronized|" +
            "do|if|private|this|break|implements|protected|throw|else|import|public|throws|case|enum|instanceof|return|transient|" +
            "catch|extends|try|final|interface|static|class|finally|strictfp|volatile|const|native|super|while)(?![A-Za-z])",Pattern.MULTILINE);

    /**
     * Regex that matches a word that starts with a capital letter, but not part of an annotation or hexadecimal
     * color declaration. Group 1 ($1) holds what was before the matched class name,
     * while group 2 ($2) holds the matched class name.
     */
    static final Pattern CLASS_NAME_REGEX = Pattern.compile("([^\\/^@^#^A-Z^a-z^0-9])([A-Z]\\w*)(?![A-Z])", Pattern.MULTILINE);

    /**
     * Regex that matches a word with parentheses after it (and optionally, a dot before it).
     * Group 1 is before the word, group 2 is the word, and group is what was after the word.
     * <p>
     * Although it's technically possible for this regex to match keywords as well, for example 'if(...', but it will be
     * used after keyword formatting, so keyword will be surrounded by font tags, and it won't be matched.
     * <p>
     * This regex does not match method references, but there is only a small amount of them inside the application,
     * so this is fine.
     */
    static final Pattern METHOD_REGEX = Pattern.compile("(\\.?)([a-z_$][a-zA-Z0-9_]*)(\\()");

    /**
     * Regex that matches a word with a dot before it (yes, it does not match member declaration, but without the dot pretty much
     * every lower case word would be matched. Group 1 is the dot, and group 2 is the word.
     */
    static final Pattern MEMBER_REGEX = Pattern.compile("(\\.)([a-z_$][a-zA-Z0-9_]*)");

    /**
     * Regex that matches comments in the code. Group 1 ($1) holds the matched comment.
     */
    static final Pattern COMMENT_REGEX = Pattern.compile("(((['\"])(?:(?!\\2|\\\\).|\\\\.)*\\2)|\\/\\/[^\\n]*|\\/\\*(?:[^*]|"
            + "\\*(?!\\/))*\\*\\/)", Pattern.MULTILINE);

    /**
     * Regex that matches the annotations in the code. Group 2 ($2) holds the matched annotation.
     */
    static final Pattern ANNOTATION_REGEX = Pattern.compile("([\\s+])(@\\w+)([\\s+])", Pattern.MULTILINE);

    //These are not used in formatting, but where formatting needs to be removed (inside comments, string literals).

    /**
     * Regex that matches formatted words. This is used to remove formatting, inside places where is isn't needed, such as comments and string literals.
     * Group 2 ($2) holds the actual phrase that was surrounded by font tags.
     */
    static final Pattern FORMATTED_REGEX = Pattern.compile("(?<=(\\<font color=\".{7}\"\\>))(.*?)(?=(\\<\\/font\\>))", Pattern.MULTILINE);

    /**
     * This will match string literals that may have formatted words inside them. It is complicated, since the formatted words
     * have quotes as well, which must not be treated as the end of the string literal.
     */
    static final Pattern FORMATTED_STRING_REGEX = Pattern.compile("(?<=\\<font color=\""+ FormatColor.TEXT_LITERAL_COLOR +"\"\\>)([\\\"].*[\\\"])(?=\\</font\\>)");
}