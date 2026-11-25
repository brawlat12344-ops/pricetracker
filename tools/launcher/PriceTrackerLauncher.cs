using System;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Threading;

namespace PriceTrackerLauncher
{
    internal static class Program
    {
        private const string FallbackRoot = @"C:\Users\user\Desktop\project DevOPs\pricetracker";
        private const string SiteUrl = "http://localhost";

        private static int Main(string[] args)
        {
            Console.OutputEncoding = Encoding.UTF8;

            string root = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            if (!File.Exists(Path.Combine(root, "docker-compose.yml")))
            {
                root = FallbackRoot;
            }

            if (!File.Exists(Path.Combine(root, "docker-compose.yml")))
            {
                Console.WriteLine("Не найден docker-compose.yml. Положите PriceTracker.exe в корень проекта.");
                return 1;
            }

            Console.WriteLine("Корень проекта: " + root);

            try
            {
                EnsureDocker(root);

                Console.WriteLine();
                Console.WriteLine("Запускаю: docker compose up -d");
                Run(root, "docker", "compose up -d");

                Console.WriteLine();
                Console.WriteLine("Жду готовности сайта...");
                if (!WaitForHealth())
                {
                    Console.WriteLine("Сайт не стал доступен за 180 секунд. Проверьте: docker compose ps -a");
                    return 2;
                }

                bool openBrowser = Array.IndexOf(args, "--no-open") < 0;
                if (openBrowser)
                {
                    Console.WriteLine("Открываю " + SiteUrl);
                    Process.Start(new ProcessStartInfo(SiteUrl) { UseShellExecute = true });
                }
                else
                {
                    Console.WriteLine("Флаг --no-open: браузер не открывался.");
                }

                Console.WriteLine();
                Console.WriteLine("Готово! PriceTracker запущен.");
                return 0;
            }
            catch (Exception ex)
            {
                Console.WriteLine();
                Console.WriteLine("ОШИБКА: " + ex.Message);
                return 3;
            }
        }

        private static void EnsureDocker(string root)
        {
            try
            {
                Run(root, "docker", "version");
                Console.WriteLine("Docker доступен.");
                return;
            }
            catch
            {
                Console.WriteLine("Docker не отвечает. Пробую запустить Docker Desktop...");
            }

            string dockerDesktop = @"C:\Program Files\Docker\Docker\Docker Desktop.exe";
            if (!File.Exists(dockerDesktop))
            {
                throw new Exception("Docker Desktop не найден по пути: " + dockerDesktop);
            }

            Process.Start(new ProcessStartInfo(dockerDesktop) { UseShellExecute = true });

            for (int i = 0; i < 60; i++)
            {
                Thread.Sleep(2000);
                try
                {
                    Run(root, "docker", "version");
                    Console.WriteLine("Docker Desktop запущен.");
                    return;
                }
                catch
                {
                    Console.Write(".");
                }
            }

            Console.WriteLine();
            throw new Exception("Docker Desktop не стал доступен за 120 секунд. Запустите его вручную.");
        }

        private static void Run(string root, string file, string arguments)
        {
            ProcessStartInfo psi = new ProcessStartInfo(file, arguments);
            psi.WorkingDirectory = root;
            psi.UseShellExecute = false;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.CreateNoWindow = true;

            using (Process process = Process.Start(psi))
            {
                string output = process.StandardOutput.ReadToEnd();
                string error = process.StandardError.ReadToEnd();
                process.WaitForExit();

                if (!string.IsNullOrWhiteSpace(output))
                {
                    Console.Write(output);
                }

                if (process.ExitCode != 0)
                {
                    throw new Exception(
                        "Команда '" + file + " " + arguments + "' завершилась с кодом " +
                        process.ExitCode + ". " + error);
                }
            }
        }

        private static bool WaitForHealth()
        {
            using (HttpClient client = new HttpClient())
            {
                client.Timeout = TimeSpan.FromSeconds(3);

                for (int i = 0; i < 90; i++)
                {
                    try
                    {
                        string body = client.GetStringAsync(SiteUrl + "/actuator/health")
                            .GetAwaiter()
                            .GetResult();

                        if (body.Contains("\"UP\""))
                        {
                            Console.WriteLine("Healthcheck: UP");
                            return true;
                        }
                    }
                    catch
                    {
                        // сайт ещё поднимается
                    }

                    Thread.Sleep(2000);
                }
            }

            return false;
        }
    }
}
