# Undo the en- and em-dash replacements made by asciidoctor-html5s
# https://github.com/jirutka/asciidoctor-html5s/blob/master/lib/asciidoctor/html5s/replacements.rb

module Asciidoctor

  [
    /(^|\n| )---( |\n|$)/,
    /(#{CG_WORD})---(?=#{CG_WORD})/,
    /(^|\n|\\| )--( |\n|$)/,
    /(#{CG_WORD})\\?--(?=#{CG_WORD})/,
  ].each do |item|
    REPLACEMENTS.delete_at REPLACEMENTS.index{|x| x[0] == item }
  end

end
