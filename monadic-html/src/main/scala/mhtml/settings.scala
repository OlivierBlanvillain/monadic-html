package mhtml

import java.util.Arrays.binarySearch
import scala.scalajs.LinkingInfo

trait MountSettings {
  /** Used to transform EntityRef (`&lambda;`) before mounting them to the DOM. */
  def transformEntityRef(entityRef: String): String

  /** Used to inspect HTML element before mounting them to the DOM. */
  def inspectElement(elem: String): Unit

  /** Used to inspect events handlers (`onclick={() => ()}`) before attaching them to the DOM. */
  def inspectEvent(event: String): Unit

  /** Used to attribute keys (`id="1"`) before mounting them to the DOM. */
  def inspectAttributeKey(att: String): Unit
}

object MountSettings {
  /** Production settings with fastOptJS a development settings with fullOptJS. */
  val default: MountSettings =
    if (LinkingInfo.productionMode)
      new ProdSettings {}
    else
      new DevSettings {}
}

/** Production mode. Does nothing but transforming EntityRef. */
trait ProdSettings extends MountSettings {
  // Converts EntityRefs according to HTML specifications; silently returning unknown keys.
  def transformEntityRef(entityRef: String): String  = {
    val i = binarySearch(EntityRefMap.keys.asInstanceOf[Array[AnyRef]], entityRef)
    if (i < 0) entityRef else EntityRefMap.values(i)
  }

  def transformAtom(value: Any): String       = value.toString
  def inspectElement(elem: String): Unit      = ()
  def inspectEvent(event: String): Unit       = ()
  def inspectAttributeKey(att: String): Unit  = ()
}

/** Development mode. Warns about unknown elements/attributes/events/EntityRef. */
trait DevSettings extends MountSettings {
  def warn(message: String): Unit = println(s"[mhtml] Warning: $message")

  // Converts EntityRefs according to HTML specifications. Warns about unknown keys.
  def transformEntityRef(entityRef: String): String  = {
    val i = binarySearch(EntityRefMap.keys.asInstanceOf[Array[AnyRef]], entityRef)
    if (i < 0) {
      warn(s"""Unknown EntityRef $entityRef. Did you mean ${EntityRefMap.keys.minBy(levenshtein(entityRef))} instead?""")
      entityRef
    } else EntityRefMap.values(i)
  }

  def inspectElement(elem: String): Unit     = isKnown("element", elements, elem)
  def inspectEvent(event: String): Unit      = isKnown("event", events, event)
  def inspectAttributeKey(att: String): Unit = isKnown("attribute", attributes, att)

  /** Warns about unknown entities. */
  def isKnown(entityName: String, knownEntites: Array[String], entityValue: String): Unit =
    if (binarySearch(knownEntites.asInstanceOf[Array[AnyRef]], entityValue) < 0)
      warn(s"""Unknown $entityName $entityValue. Did you mean ${knownEntites.minBy(levenshtein(entityValue))} instead?""")

  /** Levenshtein distance. Implementation based on Wikipedia's algorithm. */
  def levenshtein(s1: String)(s2: String): Int = {
    val dist = Array.tabulate(s2.length + 1, s1.length + 1) { (j, i) =>
      if (j == 0) i else if (i == 0) j else 0
    }

    for(j <- 1 to s2.length; i <- 1 to s1.length)
      dist(j)(i) =
        if (s2(j - 1) == s1(i - 1))
          dist(j - 1)(i - 1)
        else
          (dist(j - 1)(i) min
          dist(j)(i - 1)  min
          dist(j - 1)(i - 1)) + 1

    dist(s2.length)(s1.length)
  }

  /**
   * Valid HTML elements, sorted according to `Ordering[String]` to be binary searchable.
   * Source: https://developer.mozilla.org/en-US/docs/Web/HTML.
   */
  lazy val elements: Array[String] = Array(
    "a"          , "abbr"       , "acronym"    , "address"    , "applet"     , "area"       , "article"    ,
    "aside"      , "audio"      , "b"          , "base"       , "basefont"   , "bdi"        , "bdo"        ,
    "bgsound"    , "big"        , "blink"      , "blockquote" , "body"       , "br"         , "button"     ,
    "canvas"     , "caption"    , "center"     , "cite"       , "code"       , "col"        , "colgroup"   ,
    "command"    , "content"    , "data"       , "datalist"   , "dd"         , "del"        , "details"    ,
    "dfn"        , "dialog"     , "dir"        , "div"        , "dl"         , "dt"         , "element"    ,
    "em"         , "embed"      , "fieldset"   , "figcaption" , "figure"     , "font"       , "footer"     ,
    "form"       , "frame"      , "frameset"   , "h1"         , "h2"         , "h3"         , "h4"         ,
    "h5"         , "h6"         , "head"       , "header"     , "hgroup"     ,  "hr"        ,
    "html"       , "i"          , "iframe"     , "image"      , "img"        , "input"      , "ins"        ,
    "isindex"    , "kbd"        , "keygen"     , "label"      , "legend"     , "li"         , "link"       ,
    "listing"    , "main"       , "map"        , "mark"       , "marquee"    , "menu"       , "menuitem"   ,
    "meta"       , "meter"      , "multicol"   , "nav"        , "nobr"       , "noembed"    , "noframes"   ,
    "noscript"   , "object"     , "ol"         , "optgroup"   , "option"     , "output"     , "p"          ,
    "param"      , "picture"    , "plaintext"  , "pre"        , "progress"   , "q"          , "rp"         ,
    "rt"         , "rtc"        , "ruby"       , "s"          , "samp"       , "script"     , "section"    ,
    "select"     , "shadow"     , "small"      , "source"     , "spacer"     , "span"       , "strike"     ,
    "strong"     , "style"      , "sub"        , "summary"    , "sup"        , "svg"        , "table"      ,
    "tbody"      , "td"         , "template"   , "textarea"   , "tfoot"      , "th"         , "thead"      ,
    "time"       , "title"      , "tr"         , "track"      , "tt"         , "u"          , "ul"         ,
    "var"        , "video"      , "wbr"        , "xmp"
  )

  /**
   * Valid HTML attributes, sorted according to `Ordering[String]` to be binary searchable.
   * Source: http://www.w3schools.com/tags/ref_attributes.asp.
   */
  lazy val attributes: Array[String] = Array(
    "accept"          , "accept-charset"  , "accesskey"       , "action"          , "alt"             ,
    "async"           , "autocomplete"    , "autofocus"       , "autoplay"        , "challenge"       ,
    "charset"         , "checked"         , "cite"            , "class"           , "cols"            ,
    "colspan"         , "content"         , "contenteditable" , "contextmenu"     , "controls"        ,
    "coords"          , "data"            , "datetime"        , "default"         , "defer"           ,
    "dir"             , "dirname"         , "disabled"        , "download"        , "draggable"       ,
    "dropzone"        , "enctype"         , "for"             , "form"            , "formaction"      ,
    "headers"         , "height"          , "hidden"          , "high"            , "href"            ,
    "hreflang"        , "http-equiv"      , "id"              , "ismap"           , "keytype"         ,
    "kind"            , "label"           , "lang"            , "list"            , "loop"            ,
    "low"             , "manifest"        , "max"             , "maxlength"       , "media"           ,
    "method"          , "min"             , "multiple"        , "muted"           , "name"            ,
    "novalidate"      , "open"            , "optimum"         , "pattern"         , "placeholder"     ,
    "poster"          , "preload"         , "readonly"        , "rel"             , "required"        ,
    "reversed"        , "rows"            , "rowspan"         , "sandbox"         , "scope"           ,
    "scoped"          , "selected"        , "shape"           , "size"            , "sizes"           ,
    "span"            , "spellcheck"      , "src"             , "srcdoc"          , "srclang"         ,
    "start"           , "step"            , "style"           , "tabindex"        , "target"          ,
    "title"           , "translate"       , "type"            , "usemap"          , "value"           ,
    "width"           , "wrap"
  )

  /**
   * Valid events, sorted according to `Ordering[String]` to be binary searchable.
   * Source: http://w3c.github.io/html/dom.html.
   */
  lazy val events: Array[String] = Array(
    "onabort"          , "onblur"           , "oncancel"         , "oncanplay"        , "oncanplaythrough" ,
    "onchange"         , "onclick"          , "onclose"          , "oncontextmenu"    , "oncopy"           ,
    "oncuechange"      , "oncut"            , "ondblclick"       , "ondrag"           , "ondragend"        ,
    "ondragenter"      , "ondragexit"       , "ondragleave"      , "ondragover"       , "ondragstart"      ,
    "ondrop"           , "ondurationchange" , "onemptied"        , "onended"          , "onerror"          ,
    "onfocus"          , "oninput"          , "oninvalid"        , "onkeydown"        , "onkeypress"       ,
    "onkeyup"          , "onload"           , "onloadeddata"     , "onloadedmetadata" , "onloadstart"      ,
    "onmousedown"      , "onmouseenter"     , "onmouseleave"     , "onmousemove"      , "onmouseout"       ,
    "onmouseover"      , "onmouseup"        , "onpaste"          , "onpause"          , "onplay"           ,
    "onplaying"        , "onprogress"       , "onratechange"     , "onreset"          , "onresize"         ,
    "onscroll"         , "onseeked"         , "onseeking"        , "onselect"         , "onshow"           ,
    "onstalled"        , "onsubmit"         , "onsuspend"        , "ontimeupdate"     , "ontoggle"         ,
    "onvolumechange"   , "onwaiting"        ,  "onwheel"
  )
}

/**
 * EntityRef Map. Keys sorted according to `Ordering[String]` to be binary searchable.
 * Source: org.apache.commons.lang3.text.translate.EntityArrays.
 */
object EntityRefMap {
  lazy val keys: Array[String] = Array(
    "AElig"  , "Aacute" , "Acirc"  , "Agrave" , "Alpha"  , "Aring"   , "Atilde", "Auml"   ,
    "Beta"   , "Ccedil" , "Chi"    , "Dagger" , "Delta"  , "ETH"     , "Eacute", "Ecirc"  ,
    "Egrave" , "Epsilon", "Eta"    , "Euml"   , "Gamma"  , "Iacute"  , "Icirc" , "Igrave" ,
    "Iota"   , "Iuml"   , "Kappa"  , "Lambda" , "Mu"     , "Ntilde"  , "Nu"    , "OElig"  ,
    "Oacute" , "Ocirc"  , "Ograve" , "Omega"  , "Omicron", "Oslash"  , "Otilde", "Ouml"   ,
    "Phi"    , "Pi"     , "Prime"  , "Psi"    , "Rho"    , "Scaron"  , "Sigma" , "THORN"  ,
    "Tau"    , "Theta"  , "Uacute" , "Ucirc"  , "Ugrave" , "Upsilon" , "Uuml"  , "Xi"     ,
    "Yacute" , "Yuml"   , "Zeta"   , "aacute" , "acirc"  , "acute"   , "aelig" , "agrave" ,
    "alefsym", "alpha"  , "amp"    , "and"    , "ang"    , "apos"    , "aring" , "asymp"  ,
    "atilde" , "auml"   , "bdquo"  , "beta"   , "brvbar" , "bull"    , "cap"   , "ccedil" ,
    "cedil"  , "cent"   , "chi"    , "circ"   , "clubs"  , "cong"    , "copy"  , "crarr"  ,
    "cup"    , "curren" , "dArr"   , "dagger" , "darr"   , "deg"     , "delta" , "diams"  ,
    "divide" , "eacute" , "ecirc"  , "egrave" , "empty"  , "emsp"    , "ensp"  , "epsilon",
    "equiv"  , "eta"    , "eth"    , "euml"   , "euro"   , "exist"   , "fnof"  , "forall" ,
    "frac12" , "frac14" , "frac34" , "frasl"  , "gamma"  , "ge"      , "gt"    , "hArr"   ,
    "harr"   , "hearts" , "hellip" , "iacute" , "icirc"  , "iexcl"   , "igrave", "image"  ,
    "infin"  , "int"    , "iota"   , "iquest" , "isin"   , "iuml"    , "kappa" , "lArr"   ,
    "lambda" , "lang"   , "laquo"  , "larr"   , "lceil"  , "ldquo"   , "le"    , "lfloor" ,
    "lowast" , "loz"    , "lrm"    , "lsaquo" , "lsquo"  , "lt"      , "macr"  , "mdash"  ,
    "micro"  , "middot" , "minus"  , "mu"     , "nabla"  , "nbsp"    , "ndash" , "ne"     ,
    "ni"     , "not"    , "notin"  , "ntilde" , "nu"     , "oacute"  , "ocirc" , "oelig"  ,
    "ograve" , "oline"  , "omega"  , "omicron", "oplus"  , "or"      , "ordf"  , "ordm"   ,
    "oslash" , "otilde" , "otimes" , "ouml"   , "para"   , "part"    , "permil", "perp"   ,
    "phi"    , "pi"     , "piv"    , "plusmn" , "pound"  , "prime"   , "prod"  , "prop"   ,
    "psi"    , "quot"   , "rArr"   , "radic"  , "rang"   , "raquo"   , "rarr"  , "rceil"  ,
    "rdquo"  , "real"   , "reg"    , "rfloor" , "rho"    , "rlm"     , "rsaquo", "rsquo"  ,
    "sbquo"  , "scaron" , "sdot"   , "sect"   , "shy"    , "sigma"   , "sigmaf", "sim"    ,
    "spades" , "sub"    , "sube"   , "sum"    , "sup"    , "sup1"    , "sup2"  , "sup3"   ,
    "supe"   , "szlig"  , "tau"    , "there4" , "theta"  , "thetasym", "thinsp", "thorn"  ,
    "tilde"  , "times"  , "trade"  , "uArr"   , "uacute" , "uarr"    , "ucirc" , "ugrave" ,
    "uml"    , "upsih"  , "upsilon", "uuml"   , "weierp" , "xi"      , "yacute", "yen"    ,
    "yuml"   , "zeta"   , "zwj"    , "zwnj"
  )

  lazy val values: Array[String] = Array(
    "Æ", "Á", "Â", "À", "Α", "Å", "Ã", "Ä", "Β", "Ç", "Χ", "‡", "Δ", "Ð", "É", "Ê",
    "È", "Ε", "Η", "Ë", "Γ", "Í", "Î", "Ì", "Ι", "Ï", "Κ", "Λ", "Μ", "Ñ", "Ν", "Œ",
    "Ó", "Ô", "Ò", "Ω", "Ο", "Ø", "Õ", "Ö", "Φ", "Π", "″", "Ψ", "Ρ", "Š", "Σ", "Þ",
    "Τ", "Θ", "Ú", "Û", "Ù", "Υ", "Ü", "Ξ", "Ý", "Ÿ", "Ζ", "á", "â", "´", "æ", "à",
    "ℵ", "α", "&", "∧", "∠", "'", "å", "≈", "ã", "ä", "„", "β", "¦", "•", "∩", "ç",
    "¸", "¢", "χ", "ˆ", "♣", "≅", "©", "↵", "∪", "¤", "⇓", "†", "↓", "°", "δ", "♦",
    "÷", "é", "ê", "è", "∅", " ", " ", "ε", "≡", "η", "ð", "ë", "€", "∃", "ƒ", "∀",
    "½", "¼", "¾", "⁄", "γ", "≥", ">", "⇔", "↔", "♥", "…", "í", "î", "¡", "ì", "ℑ",
    "∞", "∫", "ι", "¿", "∈", "ï", "κ", "⇐", "λ", "〈","«", "←", "⌈", "“", "≤", "⌊",
    "∗", "◊", "‎",  "‹", "‘", "<", "¯", "—", "µ", "·", "−", "μ", "∇", " ", "–", "≠",
    "∋", "¬", "∉", "ñ", "ν", "ó", "ô", "œ", "ò", "‾", "ω", "ο", "⊕", "∨", "ª", "º",
    "ø", "õ", "⊗", "ö", "¶", "∂", "‰", "⊥", "φ", "π", "ϖ", "±", "£", "′", "∏", "∝",
    "ψ", "\"","⇒", "√", "〉","»", "→", "⌉", "”", "ℜ", "®", "⌋", "ρ", "‏",  "›", "’",
    "‚", "š", "⋅", "§", "­", "σ", "ς", "∼", "♠", "⊂", "⊆", "∑", "⊃", "¹", "²", "³",
    "⊇", "ß", "τ", "∴", "θ", "ϑ", " ", "þ", "˜", "×", "™", "⇑", "ú", "↑", "û", "ù",
    "¨", "ϒ", "υ", "ü", "℘", "ξ", "ý", "¥", "ÿ", "ζ", "‍", "‌"
  )
}
