# Loaded by KihraMaker before the game's own scripts. Generated settings: __RGSS__ = RGSS version (1, 2 or 3).
KIHRA_RGSS = __RGSS__
KIHRA_FFI = __FFI__

# Progress marks the app reads to tell "still loading" from "stuck" (and to explain a hang).
module KihraBoot
  def self.mark(text)
    File.open("kihra_boot.txt", "a") { |f| f.puts("#{Time.now.to_f}\t#{text}") }
  rescue StandardError
  end
end
KihraBoot.mark("shim loaded (RGSS #{KIHRA_RGSS})")

# ---- Old Ruby (1.9) methods that RPG Maker VX Ace games still use ----
begin
  class Hash
    unless method_defined?(:index)
      def index(value)
        key(value)
      end
    end
  end
  Fixnum = Integer unless defined?(Fixnum)
  Bignum = Integer unless defined?(Bignum)
rescue StandardError
end

# ---- Windows-only DLLs (steam_api, plugins) can't load here: calls become harmless no-ops ----
if KIHRA_FFI
begin
  module KihraSafeFFI
    SEEN = {}
    def self.log(lib, func, args)
      text = args.map { |a| a.is_a?(String) ? a.inspect : a.to_s }.join(", ")
      return unless func =~ /achiev|unlock|trophy/i || text =~ /achiev|unlock|trophy/i
      line = lib + "\t" + func + "\t" + text
      return if SEEN[line]
      SEEN[line] = true
      begin
        File.open("kihra_ach.log", "a") { |f| f.puts line }
      rescue StandardError
      end
    end
    def initialize(lib, func, *rest, &blk)
      super
    rescue RuntimeError, LoadError, NameError
      @kihra_missing = true
      @kihra_lib = lib.to_s
      @kihra_func = func.to_s
    end
    def call(*args)
      if @kihra_missing
        KihraSafeFFI.log(@kihra_lib, @kihra_func, args)
        return 0
      end
      super
    end
    def Call(*args)
      call(*args)
    end
  end
  MiniFFI.send(:prepend, KihraSafeFFI)
rescue StandardError
end
end

# ---- Light hook: first-frame mark, and loads the cheat code only when a command arrives ----
begin
  module KihraGraphicsHook
    def update(*args)
      r = super
      unless @kihra_first
        @kihra_first = true
        KihraBoot.mark("first frame")
      end
      @kihra_n = (@kihra_n || 0) + 1
      if @kihra_n % 8 == 0 && (@kihra_ctl || File.exist?("kihra_cmd.txt"))
        begin
          unless @kihra_ctl
            load "kihra_ctl.rb"
            @kihra_ctl = true
          end
          KihraCtl.poll
        rescue Exception
        end
      end
      r
    end
  end
  Graphics.singleton_class.prepend(KihraGraphicsHook)
rescue StandardError
end
