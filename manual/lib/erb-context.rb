require 'erb'
require 'json'
require_relative 'util'

# Context object to provide variables and methods to the ERB templates
class ERBContext

  PARAMS = attr_reader *%i(
    title content
    document attributes
    authors translators
    language languages
    mod_version
    manual_toc page_toc
    url_path output_path
    display_branch noindex canonical_url_base
  )

  attr_writer :title, :content

  def initialize(opts)
    PARAMS.each do |k|
      instance_variable_set :"@#{k}", opts[k]
    end
  end

  def binding
    Kernel.binding
  end

  def h(value)
    ERB::Util.html_escape(value)
  end

  def t(key)
    attributes.fetch(key, key)
  end

  def lang_name(lang)
    Util.lang_name(lang)
  end

  def attach_cache_id(path)
    Util.add_cache_id(path, output_path)
  end

end
