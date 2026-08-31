require 'erb'
require_relative 'util'
require_relative 'erb-context'

class ManualBuilder

  PARAMS = attr_reader *%i(
    layout_path
    source_path_tmpl
    attributes_path_tmpl
    po_path_tmpl
    output_path_tmpl
    languages
  )

  def initialize(opts)
    PARAMS.each do |k|
      instance_variable_set :"@#{k}", opts.fetch(k)
    end
  end

  def layout
    @layout ||= ERB.new File.read layout_path
  end

  def page(source_path: String, output_path: String, lang: Symbol)
    Page.new(self, source_path: source_path, output_path: output_path, lang: lang)
  end

  def document(name: String, lang: Symbol)
    Document.new(self, name: name, lang: lang)
  end

  class Page

    attr_reader :builder, :source_path, :output_path, :lang

    def initialize(builder, source_path: String, output_path: String, lang: Symbol)
      @builder = builder
      @source_path = source_path
      @output_path = output_path
      @lang = lang
    end

    def build
      ctx = ERBContext.new(
        title: @title,
        content: @content,
        document: @document,
        authors: @authors,
        translators: @translators,
        attributes: attributes,
        language: lang,
        languages: languages,
        manual_toc: manual_toc,
        page_toc: @page_toc,
        output_path: output_path,
      )

      unless @content
        template = ERB.new File.read source_path
        ctx.content = template.result ctx.binding
      end

      html = layout.result ctx.binding

      FileUtils.mkdir_p File.dirname output_path
      File.write output_path, html
    end

    def adoc_options
      {
        safe: :safe,
        backend: 'html5s',
        parse: true,
        standalone: false,
        attributes: {
          'imagesdir' => '../images',
        }.merge(attributes.map{|k, v| ["#{k}@", v]}.to_h),
      }
    end

    # Translatable attributes
    def attributes
      res = attributes_paths.map{|f| Util.flatten_hash YAML.load_file f }
      {}.merge(*res)
    end

    # Read index.adoc for a TOC of the entire manual
    def manual_toc
      doc = Asciidoctor.load_file index_path, adoc_options
      toc = doc.find_by(id: 'manual-toc')[0].convert
      Util.html_strip toc, layers: 2
    end

    %i(source attributes po output).map{|k| :"#{k}_path_tmpl" }
    .append(:languages, :layout, :layout_path)
    .each do |k|
      define_method k do
        builder.__send__ k
      end
    end

    def attributes_paths
      default = attributes_path_tmpl.(lang: languages.default)
      local = attributes_path_tmpl.(lang: lang)
      if !languages.default?(lang) && File.exist?(local)
        [default, local]
      else
        [default]
      end
    end

    def index_path
      [lang, languages.default]
      .map{|x| source_path_tmpl.(lang: x, name: 'index') }
      .find(&File.method(:exist?))
    end

    def dependency_files
      ([source_path, index_path, layout_path] + attributes_paths).compact
    end

  end

  class Document < Page

    attr_reader :name

    def initialize(builder, name: String, lang: Symbol)
      super(builder, lang: lang)
      @name = name
    end

    def build
      @document = Asciidoctor.load_file source_path, adoc_options

      @title = @document.title
      @content = Util.cache_id_images(@document.convert, output_path)

      @page_toc = @document.converter.convert @document, 'outline', toclevels: 2
      @page_toc = Util.html_strip @page_toc, strip_class: true

      @authors = Util.git_file_authors(source_path_tmpl.(name: name, lang: languages.default))
      @translators = Util.git_file_authors(po_path_tmpl.(name: name, lang: lang)) - @authors unless languages.default?(lang)

      super
    end

    def source_path
      source_path_tmpl.(name: name, lang: lang)
    end

    def output_path
      output_path_tmpl.(name: name, lang: lang)
    end

  end

end
