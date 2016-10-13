package mhtml

import java.util.Arrays

object EntityRefMap {
  def apply(key: String): String = {
    val i = Arrays.binarySearch(keys.asInstanceOf[Array[AnyRef]], key)
    if (i < 0) key else values(i)
  }

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
